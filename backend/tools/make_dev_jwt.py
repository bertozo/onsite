#!/usr/bin/env python3
"""Mint a local HS256 JWT shaped like the ones Supabase Auth issues, so the backend's
auth flow can be exercised end to end before a real Supabase project exists.

Usage:
    python tools/make_dev_jwt.py [email] [secret]

Defaults: email=demo@example.com, secret=$SUPABASE_JWT_SECRET or the .env.example value.
The same secret must be configured as SUPABASE_JWT_SECRET on the server for verification
to succeed. Prints the token and the curl commands to try it against a local server.
"""
import base64
import hashlib
import hmac
import json
import os
import sys
import time
import uuid


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def make_jwt(secret: str, email: str, issuer: str = "supabase", ttl_seconds: int = 3600) -> str:
    header = {"alg": "HS256", "typ": "JWT"}
    now = int(time.time())
    payload = {
        "sub": str(uuid.uuid5(uuid.NAMESPACE_DNS, email)),
        "email": email,
        "iss": issuer,
        "iat": now,
        "exp": now + ttl_seconds,
        "role": "authenticated",
    }
    signing_input = f"{b64url(json.dumps(header).encode())}.{b64url(json.dumps(payload).encode())}"
    signature = hmac.new(secret.encode(), signing_input.encode(), hashlib.sha256).digest()
    return f"{signing_input}.{b64url(signature)}"


if __name__ == "__main__":
    email = sys.argv[1] if len(sys.argv) > 1 else "demo@example.com"
    secret = sys.argv[2] if len(sys.argv) > 2 else os.environ.get("SUPABASE_JWT_SECRET", "dev-only-secret-change-me")

    token = make_jwt(secret, email)
    print(f"email:  {email}")
    print(f"secret: {secret}")
    print(f"token:  {token}")
    print()
    print("Try it:")
    print(f'curl -s -X POST http://localhost:8080/v1/me/bootstrap -H "Authorization: Bearer {token}" | python -m json.tool')
    print(f'curl -s http://localhost:8080/v1/me -H "Authorization: Bearer {token}" | python -m json.tool')
