"""
Seeds realistic demo data into a copy of the app's Room database (schema v11).

Usage (debug build, device connected):
  adb shell pm clear com.xbertz.onsite            # wipe app data
  adb shell am start -n com.xbertz.onsite/.MainActivity   # let Room create the empty v11 db, then
  adb shell am force-stop com.xbertz.onsite
  adb exec-out "run-as com.xbertz.onsite cat databases/onsite.db" > onsite.db      # also -wal and -shm
  python tools/seed_demo_data.py onsite.db
  adb push onsite.db /data/local/tmp/onsite.db
  adb shell "run-as com.xbertz.onsite sh -c 'rm -f databases/onsite.db-wal databases/onsite.db-shm && cp /data/local/tmp/onsite.db databases/onsite.db'"

Invoices are not seeded (their PDFs must exist); generate them from the app afterwards.
"""
import sqlite3, random, time, sys
from datetime import datetime, date, timedelta, timezone

DB = sys.argv[1] if len(sys.argv) > 1 else 'onsite.db'
TZ = timezone(timedelta(hours=10))  # AEST; no DST in Aug-Sep 2026
TODAY = date(2026, 9, 17)
random.seed(42)

WEIGHTS = [10, 1, 3, 5, 7, 9, 11, 13, 15, 17, 19]


def abn_ok(d):
    return sum((int(c) - (1 if i == 0 else 0)) * WEIGHTS[i] for i, c in enumerate(d)) % 89 == 0


def make_abn(prefix):
    for tail in range(10 ** (11 - len(prefix))):
        d = prefix + str(tail).zfill(11 - len(prefix))
        if abn_ok(d):
            return f'{d[:2]} {d[2:5]} {d[5:8]} {d[8:]}'
    raise RuntimeError


def ms(d, h, m):
    return int(datetime(d.year, d.month, d.day, h, m, tzinfo=TZ).timestamp() * 1000)


now = int(time.time() * 1000)
c = sqlite3.connect(DB)
c.execute('PRAGMA journal_mode=WAL')
for t in ['tracking_sessions', 'companies', 'profile', 'sites', 'job_types', 'planned_jobs', 'invoices']:
    c.execute(f'DELETE FROM {t}')
c.execute("DELETE FROM sqlite_sequence")

# Profile (single row, id = 1)
c.execute('INSERT INTO profile VALUES (1,?,?,?,?,?,?,?,?)', (
    'Marcos Ferreira', '0412 987 654', 'marcos.ferreira.tiling@gmail.com', 'Wall & Floor Tiler',
    make_abn('51824'), None, '062-000', '12345678'))

companies = [
    ('Bayside Renovations Pty Ltd', make_abn('33102'), '02 9130 4455', 'accounts@baysidereno.com.au', 65.0),
    ('Northshore Builders', make_abn('27455'), '0433 210 778', 'invoices@northshorebuilders.com.au', 70.0),
    ('Harbour City Property Services', make_abn('19876'), '02 9635 0021', 'ap@harbourcityps.com.au', 60.0),
]
for name, abn, phone, email, rate in companies:
    c.execute('INSERT INTO companies (name, abn, phone, email, createdAtMillis, hourlyRate) VALUES (?,?,?,?,?,?)',
              (name, abn, phone, email, now - 90 * 86400000, rate))

sites = {
    'Bondi beach house': ('14 Campbell Parade, Bondi Beach NSW 2026', -33.8908, 151.2743),
    'Manly kitchen reno': ('28 Whistler Street, Manly NSW 2095', -33.7969, 151.2872),
    'Chatswood apartments': ('412 Victoria Avenue, Chatswood NSW 2067', -33.7969, 151.1833),
    'Parramatta office fit-out': ('60 Phillip Street, Parramatta NSW 2150', -33.8150, 151.0011),
    'Cronulla bathroom': ('7 Gerrale Street, Cronulla NSW 2230', -34.0553, 151.1531),
}
for label, (addr, lat, lon) in sites.items():
    c.execute('INSERT INTO sites (label, address, latitude, longitude, createdAtMillis) VALUES (?,?,?,?,?)',
              (label, addr, lat, lon, now - 80 * 86400000))

job_types = ['Tiling', 'Waterproofing', 'Grouting & sealing', 'Screeding', 'Site clean-up']
for jt in job_types:
    c.execute('INSERT INTO job_types (name, createdAtMillis) VALUES (?,?)', (jt, now - 80 * 86400000))

# Work plan: which client/site/job each weekday belongs to, week by week (Mon 10 Aug .. Thu 17 Sep).
plan = {
    # week of 10 Aug: Bondi (Bayside)
    date(2026, 8, 10): ('Bayside Renovations Pty Ltd', 'Bondi beach house', 'Screeding'),
    date(2026, 8, 11): ('Bayside Renovations Pty Ltd', 'Bondi beach house', 'Waterproofing'),
    date(2026, 8, 12): ('Bayside Renovations Pty Ltd', 'Bondi beach house', 'Tiling'),
    date(2026, 8, 13): ('Bayside Renovations Pty Ltd', 'Bondi beach house', 'Tiling'),
    date(2026, 8, 14): ('Bayside Renovations Pty Ltd', 'Bondi beach house', 'Tiling'),
    # week of 17 Aug: Chatswood (Northshore), Wed off
    date(2026, 8, 17): ('Northshore Builders', 'Chatswood apartments', 'Waterproofing'),
    date(2026, 8, 18): ('Northshore Builders', 'Chatswood apartments', 'Tiling'),
    date(2026, 8, 20): ('Northshore Builders', 'Chatswood apartments', 'Tiling'),
    date(2026, 8, 21): ('Northshore Builders', 'Chatswood apartments', 'Grouting & sealing'),
    # week of 24 Aug: Bondi finish + Parramatta
    date(2026, 8, 24): ('Bayside Renovations Pty Ltd', 'Bondi beach house', 'Grouting & sealing'),
    date(2026, 8, 25): ('Bayside Renovations Pty Ltd', 'Bondi beach house', 'Site clean-up'),
    date(2026, 8, 26): ('Harbour City Property Services', 'Parramatta office fit-out', 'Screeding'),
    date(2026, 8, 27): ('Harbour City Property Services', 'Parramatta office fit-out', 'Tiling'),
    date(2026, 8, 28): ('Harbour City Property Services', 'Parramatta office fit-out', 'Tiling'),
    # week of 31 Aug: Parramatta + Saturday
    date(2026, 8, 31): ('Harbour City Property Services', 'Parramatta office fit-out', 'Tiling'),
    date(2026, 9, 1): ('Harbour City Property Services', 'Parramatta office fit-out', 'Grouting & sealing'),
    date(2026, 9, 2): ('Northshore Builders', 'Chatswood apartments', 'Tiling'),
    date(2026, 9, 3): ('Northshore Builders', 'Chatswood apartments', 'Tiling'),
    date(2026, 9, 4): ('Northshore Builders', 'Chatswood apartments', 'Tiling'),
    date(2026, 9, 5): ('Northshore Builders', 'Chatswood apartments', 'Tiling'),  # Saturday, premium rate
    # week of 7 Sep: Manly (Bayside), Fri off (rain)
    date(2026, 9, 7): ('Bayside Renovations Pty Ltd', 'Manly kitchen reno', 'Screeding'),
    date(2026, 9, 8): ('Bayside Renovations Pty Ltd', 'Manly kitchen reno', 'Waterproofing'),
    date(2026, 9, 9): ('Bayside Renovations Pty Ltd', 'Manly kitchen reno', 'Tiling'),
    date(2026, 9, 10): ('Bayside Renovations Pty Ltd', 'Manly kitchen reno', 'Tiling'),
    # week of 14 Sep: Manly + Cronulla start
    date(2026, 9, 14): ('Bayside Renovations Pty Ltd', 'Manly kitchen reno', 'Grouting & sealing'),
    date(2026, 9, 15): ('Northshore Builders', 'Cronulla bathroom', 'Waterproofing'),
    date(2026, 9, 16): ('Northshore Builders', 'Cronulla bathroom', 'Tiling'),
    date(2026, 9, 17): ('Northshore Builders', 'Cronulla bathroom', 'Tiling'),
}
premium_days = {date(2026, 9, 5): 95.0}          # Saturday rate override
lunch_split_days = {date(2026, 8, 12), date(2026, 8, 27), date(2026, 9, 9), date(2026, 9, 16)}

for d, (company, site, job) in sorted(plan.items()):
    _, lat, lon = sites[site]
    start_h, start_m = 6 + random.choice([0, 1, 1, 1]), random.choice([30, 45, 0, 15])
    rate = premium_days.get(d)
    if d == TODAY:
        # Today: the morning block is done; the afternoon is still to come.
        blocks = [((7, 5), (11, 40))]
    elif d in lunch_split_days:
        blocks = [((start_h, start_m), (12, 0)), ((12, 35), (15, random.choice([30, 45, 50])))]
    else:
        end_h = 15 + random.choice([0, 0, 1, 1, 2])
        blocks = [((start_h, start_m), (end_h, random.choice([0, 15, 30, 45])))]
    for (sh, sm), (eh, em) in blocks:
        jitter = lambda: random.uniform(-0.0004, 0.0004)
        c.execute('INSERT INTO tracking_sessions (companyName, siteLabel, jobTypeLabel, startTimestampMillis, startLatitude, startLongitude, '
                  'stopTimestampMillis, stopLatitude, stopLongitude, hourlyRate, invoiceId) VALUES (?,?,?,?,?,?,?,?,?,?,NULL)',
                  (company, site, job, ms(d, sh, sm), lat + jitter(), lon + jitter(), ms(d, eh, em), lat + jitter(), lon + jitter(), rate))

# Planned jobs for the coming days
def pj(d, sm, em, company, site, job, notes=None):
    c.execute('INSERT INTO planned_jobs (dateEpochDay, startMinute, endMinute, companyName, siteLabel, jobTypeLabel, notes) VALUES (?,?,?,?,?,?,?)',
              ((d - date(1970, 1, 1)).days, sm, em, company, site, job, notes))

pj(date(2026, 9, 18), 7 * 60, 15 * 60 + 30, 'Northshore Builders', 'Cronulla bathroom', 'Tiling', 'Finish shower niche, bring 600x300 porcelain')
pj(date(2026, 9, 21), 7 * 60, 15 * 60, 'Northshore Builders', 'Cronulla bathroom', 'Grouting & sealing')
pj(date(2026, 9, 22), 7 * 60 + 30, 12 * 60, 'Northshore Builders', 'Cronulla bathroom', 'Site clean-up', 'Handover 1pm')
pj(date(2026, 9, 23), 8 * 60, None, 'Bayside Renovations Pty Ltd', 'Bondi beach house', 'Tiling', 'Quote for outdoor deck tiles')
pj(date(2026, 9, 28), 7 * 60, 15 * 60 + 30, 'Harbour City Property Services', 'Parramatta office fit-out', 'Tiling', 'Level 3 kitchenette')
pj(date(2026, 9, 29), 7 * 60, 15 * 60 + 30, 'Harbour City Property Services', 'Parramatta office fit-out', 'Tiling')
pj(date(2026, 9, 30), 7 * 60, 15 * 60 + 30, 'Harbour City Property Services', 'Parramatta office fit-out', 'Grouting & sealing')

c.commit()
n = c.execute('SELECT count(*) FROM tracking_sessions').fetchone()[0]
hours = c.execute('SELECT sum(stopTimestampMillis-startTimestampMillis)/3600000.0 FROM tracking_sessions').fetchone()[0]
print('sessions', n, 'hours %.1f' % hours, 'companies', c.execute('SELECT count(*) FROM companies').fetchone()[0], 'planned', c.execute('SELECT count(*) FROM planned_jobs').fetchone()[0])
c.execute('PRAGMA wal_checkpoint(TRUNCATE)')
c.close()
