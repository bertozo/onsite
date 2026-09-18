"""
Seeds one year of realistic rope-access-business demo data into a copy of the
app's Room database (schema v11): a sole-trader IRATA rope access technician
(profile "John Smith") working for four clients across five sites.

Usage (debug build, device connected):
  adb shell pm clear com.xbertz.onsite
  adb shell am start -n com.xbertz.onsite/.MainActivity   # let Room create the empty v11 db, then
  adb shell am force-stop com.xbertz.onsite
  adb exec-out "run-as com.xbertz.onsite cat databases/onsite.db" > onsite.db      # also -wal and -shm
  python tools/seed_demo_data.py onsite.db
  adb push onsite.db /data/local/tmp/onsite.db
  adb shell "run-as com.xbertz.onsite sh -c 'rm -f databases/onsite.db-wal databases/onsite.db-shm && cp /data/local/tmp/onsite.db databases/onsite.db'"

Invoices are not seeded (their PDFs must exist as real files); generate a few
from the app afterwards, e.g. from Reports -> Generate invoice.
"""
import sqlite3
import random
import sys
import time
from datetime import date, datetime, timedelta, timezone

DB = sys.argv[1] if len(sys.argv) > 1 else 'onsite.db'
TODAY = date(2026, 9, 17)
random.seed(7)

WEIGHTS = [10, 1, 3, 5, 7, 9, 11, 13, 15, 17, 19]


def abn_ok(d):
    return sum((int(c) - (1 if i == 0 else 0)) * WEIGHTS[i] for i, c in enumerate(d)) % 89 == 0


def make_abn(prefix):
    for tail in range(10 ** (11 - len(prefix))):
        d = prefix + str(tail).zfill(11 - len(prefix))
        if abn_ok(d):
            return f'{d[:2]} {d[2:5]} {d[5:8]} {d[8:]}'
    raise RuntimeError(f'no valid ABN found for prefix {prefix}')


def first_sunday(year, month):
    d = date(year, month, 1)
    return d + timedelta(days=(6 - d.weekday()) % 7)


def sydney_offset(d):
    """AU east-coast DST: first Sunday of Oct to first Sunday of April is AEDT (+11), else AEST (+10)."""
    if d < first_sunday(d.year, 4):
        return 11
    if d >= first_sunday(d.year, 10):
        return 11
    return 10


def ms(d, h, m):
    tz = timezone(timedelta(hours=sydney_offset(d)))
    return int(datetime(d.year, d.month, d.day, h, m, tzinfo=tz).timestamp() * 1000)


def weekdays(start, end):
    days, d = [], start
    while d <= end:
        if d.weekday() < 5:
            days.append(d)
        d += timedelta(days=1)
    return days


now = int(time.time() * 1000)
conn = sqlite3.connect(DB)
conn.execute('PRAGMA journal_mode=WAL')
for t in ['tracking_sessions', 'companies', 'profile', 'sites', 'job_types', 'planned_jobs', 'invoices']:
    conn.execute(f'DELETE FROM {t}')
conn.execute('DELETE FROM sqlite_sequence')

# --- Profile -----------------------------------------------------------------
conn.execute('INSERT INTO profile VALUES (1,?,?,?,?,?,?,?,?)', (
    'John Smith', '0478 123 456', 'johnsmithtest@test.com', 'IRATA Level 3 Rope Access Technician',
    make_abn('77812'), None, '032-001', '87654321'
))

# --- Companies (clients) -------------------------------------------------------
COMPANIES = [
    ('Meridian Property Group', make_abn('64230'), '02 8811 4420', 'accounts@meridianproperty.com.au', 85.0),
    ('Southern Cross Wind Energy', make_abn('58917'), '02 6238 4471', 'contractors@southerncrosswind.com.au', 120.0),
    ('National Tower Networks Pty Ltd', make_abn('41276'), '02 6452 3390', 'ap@ntntowers.com.au', 95.0),
    ('Harbourside Construction Group', make_abn('29845'), '02 9552 1187', 'invoices@harboursideconstruction.com.au', 90.0),
]
for name, abn, phone, email, rate in COMPANIES:
    conn.execute(
        'INSERT INTO companies (name, abn, phone, email, createdAtMillis, hourlyRate) VALUES (?,?,?,?,?,?)',
        (name, abn, phone, email, now - 370 * 86400000, rate)
    )

# --- Sites ---------------------------------------------------------------------
SITES = {
    'Meridian Tower - George St': ('201 George Street, Sydney NSW 2000', -33.8618, 151.2108),
    'Meridian Tower - North Sydney': ('100 Miller Street, North Sydney NSW 2060', -33.8404, 151.2073),
    'Capital Wind Farm': ('Capital Wind Farm, Tarago NSW 2580', -34.9333, 149.6667),
    'Cooma Communications Tower': ('Mittagang Road, Cooma NSW 2630', -36.2350, 149.1250),
    'Harbourside Construction - Pyrmont': ('45 Pirrama Road, Pyrmont NSW 2009', -33.8688, 151.1957),
}
for label, (addr, lat, lon) in SITES.items():
    conn.execute(
        'INSERT INTO sites (label, address, latitude, longitude, createdAtMillis) VALUES (?,?,?,?,?)',
        (label, addr, lat, lon, now - 365 * 86400000)
    )

# --- Services (job types) -------------------------------------------------------
JOB_TYPES = [
    'Facade Inspection', 'Window Cleaning', 'Gutter & Downpipe Cleaning', 'Bird Proofing & Netting',
    'Turbine Blade Inspection', 'Tower Structural Inspection', 'Edge Protection Installation',
]
for jt in JOB_TYPES:
    conn.execute('INSERT INTO job_types (name, createdAtMillis) VALUES (?,?)', (jt, now - 365 * 86400000))


def add_session(company, site, job, d, sh, sm, eh, em, rate=None):
    _, lat, lon = SITES[site]
    jitter = lambda: random.uniform(-0.0006, 0.0006)
    conn.execute(
        'INSERT INTO tracking_sessions (companyName, siteLabel, jobTypeLabel, startTimestampMillis, startLatitude, '
        'startLongitude, stopTimestampMillis, stopLatitude, stopLongitude, hourlyRate, invoiceId) '
        'VALUES (?,?,?,?,?,?,?,?,?,?,NULL)',
        (company, site, job, ms(d, sh, sm), lat + jitter(), lon + jitter(),
         ms(d, eh, em), lat + jitter(), lon + jitter(), rate)
    )


# One block of work at one client/site, spanning several weeks. `jobs` splits the
# weekdays into equal chunks, one service per chunk (a two-week facade job might be
# a week of inspection followed by a week of cleaning, say). `standdown` are weekdays
# with no work at all (a storm/high-wind call-off for the wind-farm jobs); `saturday`
# is a single make-up day the following weekend at a premium rate; `lunch_split`
# breaks a day into a morning and afternoon session either side of a lunch break.
CAMPAIGNS = [
    dict(start=date(2025, 9, 22), end=date(2025, 10, 3), company='Meridian Property Group',
         site='Meridian Tower - George St', jobs=['Facade Inspection', 'Window Cleaning'],
         lunch_split=[date(2025, 10, 1)]),
    dict(start=date(2025, 10, 13), end=date(2025, 10, 17), company='Harbourside Construction Group',
         site='Harbourside Construction - Pyrmont', jobs=['Edge Protection Installation']),
    dict(start=date(2025, 10, 27), end=date(2025, 11, 7), company='Southern Cross Wind Energy',
         site='Capital Wind Farm', jobs=['Turbine Blade Inspection'],
         standdown=[date(2025, 10, 30)], saturday=date(2025, 11, 8), saturday_rate=180.0),
    dict(start=date(2025, 11, 17), end=date(2025, 11, 21), company='Meridian Property Group',
         site='Meridian Tower - North Sydney', jobs=['Gutter & Downpipe Cleaning']),
    dict(start=date(2025, 12, 1), end=date(2025, 12, 12), company='National Tower Networks Pty Ltd',
         site='Cooma Communications Tower', jobs=['Tower Structural Inspection']),
    # Christmas/New Year break: no campaigns from 2025-12-15 to 2026-01-11.
    dict(start=date(2026, 1, 12), end=date(2026, 1, 23), company='Meridian Property Group',
         site='Meridian Tower - George St', jobs=['Bird Proofing & Netting'],
         lunch_split=[date(2026, 1, 21)]),
    dict(start=date(2026, 2, 2), end=date(2026, 2, 6), company='Harbourside Construction Group',
         site='Harbourside Construction - Pyrmont', jobs=['Edge Protection Installation']),
    dict(start=date(2026, 2, 16), end=date(2026, 2, 27), company='Southern Cross Wind Energy',
         site='Capital Wind Farm', jobs=['Turbine Blade Inspection'],
         standdown=[date(2026, 2, 19)], saturday=date(2026, 2, 28), saturday_rate=180.0),
    dict(start=date(2026, 3, 9), end=date(2026, 3, 13), company='Meridian Property Group',
         site='Meridian Tower - North Sydney', jobs=['Facade Inspection']),
    dict(start=date(2026, 3, 23), end=date(2026, 4, 3), company='National Tower Networks Pty Ltd',
         site='Cooma Communications Tower', jobs=['Tower Structural Inspection'],
         lunch_split=[date(2026, 4, 1)]),
    dict(start=date(2026, 4, 13), end=date(2026, 4, 17), company='Meridian Property Group',
         site='Meridian Tower - George St', jobs=['Window Cleaning']),
    dict(start=date(2026, 5, 4), end=date(2026, 5, 15), company='Southern Cross Wind Energy',
         site='Capital Wind Farm', jobs=['Turbine Blade Inspection'],
         standdown=[date(2026, 5, 7)], saturday=date(2026, 5, 16), saturday_rate=180.0),
    dict(start=date(2026, 5, 25), end=date(2026, 5, 29), company='Harbourside Construction Group',
         site='Harbourside Construction - Pyrmont', jobs=['Edge Protection Installation']),
    dict(start=date(2026, 6, 8), end=date(2026, 6, 19), company='Meridian Property Group',
         site='Meridian Tower - North Sydney', jobs=['Gutter & Downpipe Cleaning', 'Bird Proofing & Netting']),
    dict(start=date(2026, 7, 6), end=date(2026, 7, 10), company='National Tower Networks Pty Ltd',
         site='Cooma Communications Tower', jobs=['Tower Structural Inspection']),
    dict(start=date(2026, 7, 20), end=date(2026, 7, 31), company='Meridian Property Group',
         site='Meridian Tower - George St', jobs=['Facade Inspection'],
         lunch_split=[date(2026, 7, 29)]),
    dict(start=date(2026, 8, 10), end=date(2026, 8, 14), company='Harbourside Construction Group',
         site='Harbourside Construction - Pyrmont', jobs=['Edge Protection Installation']),
    dict(start=date(2026, 8, 24), end=date(2026, 9, 4), company='Southern Cross Wind Energy',
         site='Capital Wind Farm', jobs=['Turbine Blade Inspection'],
         standdown=[date(2026, 8, 27)], saturday=date(2026, 9, 5), saturday_rate=180.0),
    # Current week: only the morning of TODAY is logged, like a session in progress.
    dict(start=date(2026, 9, 14), end=date(2026, 9, 17), company='Meridian Property Group',
         site='Meridian Tower - North Sydney', jobs=['Window Cleaning'], today_partial=True),
]

for camp in CAMPAIGNS:
    days = [d for d in weekdays(camp['start'], camp['end']) if d not in camp.get('standdown', [])]
    jobs = camp['jobs']
    chunk_size = -(-len(days) // len(jobs))  # ceil division
    chunks = [days[i:i + chunk_size] for i in range(0, len(days), chunk_size)]
    for job, chunk_days in zip(jobs, chunks):
        for d in chunk_days:
            if camp.get('today_partial') and d == TODAY:
                add_session(camp['company'], camp['site'], job, d, 7, 5, 11, 40)
                continue
            start_h, start_m = random.choice([6, 7, 7, 7]), random.choice([0, 15, 30, 45])
            if d in camp.get('lunch_split', []):
                add_session(camp['company'], camp['site'], job, d, start_h, start_m, 12, 0)
                add_session(camp['company'], camp['site'], job, d, 12, 35, 15, random.choice([30, 45, 50]))
            else:
                end_h = min(start_h + random.choice([7, 8, 8, 9]), 17)
                add_session(camp['company'], camp['site'], job, d, start_h, start_m, end_h, random.choice([0, 15, 30, 45]))
    if camp.get('saturday'):
        add_session(camp['company'], camp['site'], jobs[-1], camp['saturday'], 7, 0, 12, 30, rate=camp.get('saturday_rate'))

# One-off out-of-hours emergency callout at a premium rate (a loose cladding panel reported after a storm).
add_session('Meridian Property Group', 'Meridian Tower - George St', 'Facade Inspection',
            date(2026, 6, 21), 9, 0, 13, 0, rate=150.0)


def pj(d, sm, em, company, site, job, notes=None):
    conn.execute(
        'INSERT INTO planned_jobs (dateEpochDay, startMinute, endMinute, companyName, siteLabel, jobTypeLabel, notes) '
        'VALUES (?,?,?,?,?,?,?)',
        ((d - date(1970, 1, 1)).days, sm, em, company, site, job, notes)
    )


pj(date(2026, 9, 18), 7 * 60, 15 * 60 + 30, 'Meridian Property Group', 'Meridian Tower - North Sydney',
   'Window Cleaning', 'Finish western facade, bring squeegees + extension poles')
pj(date(2026, 9, 21), 7 * 60, 15 * 60 + 30, 'Harbourside Construction Group', 'Harbourside Construction - Pyrmont',
   'Edge Protection Installation')
pj(date(2026, 9, 22), 7 * 60, 15 * 60 + 30, 'Harbourside Construction Group', 'Harbourside Construction - Pyrmont',
   'Edge Protection Installation')
pj(date(2026, 9, 23), 7 * 60, 12 * 60, 'Harbourside Construction Group', 'Harbourside Construction - Pyrmont',
   'Edge Protection Installation', 'Half day - handover to site foreman 1pm')
pj(date(2026, 10, 5), 6 * 60 + 30, None, 'Southern Cross Wind Energy', 'Capital Wind Farm',
   'Turbine Blade Inspection', 'IRATA re-cert renewed - check rope kit before departure')
pj(date(2026, 10, 19), 7 * 60, 15 * 60, 'National Tower Networks Pty Ltd', 'Cooma Communications Tower',
   'Tower Structural Inspection', 'Annual structural cert - NTN compliance report due')

conn.commit()
n = conn.execute('SELECT count(*) FROM tracking_sessions').fetchone()[0]
hours = conn.execute('SELECT sum(stopTimestampMillis - startTimestampMillis) / 3600000.0 FROM tracking_sessions').fetchone()[0]
print('sessions', n, 'hours %.1f' % hours,
      'companies', conn.execute('SELECT count(*) FROM companies').fetchone()[0],
      'sites', conn.execute('SELECT count(*) FROM sites').fetchone()[0],
      'services', conn.execute('SELECT count(*) FROM job_types').fetchone()[0],
      'planned', conn.execute('SELECT count(*) FROM planned_jobs').fetchone()[0])
conn.execute('PRAGMA wal_checkpoint(TRUNCATE)')
conn.close()
