#!/usr/bin/env python3
"""Attach images to products in bulk through the yammer API.

Put one image per product in a folder, named after the product (case, spaces, punctuation
and diacritics don't matter): "beefeater & tonic.jpg", "S. Pellegrino 0.5 L.png", ...
JPG, PNG or WEBP, up to 5 MB each; the customer menu shows them as 64x64 cover thumbnails,
so ~400x400 square crops are plenty.

    python3 db-seeds/upload-product-images.py --user admin --location 4f884711-7c5e-41a7-9aec-338208ef3dba --dir ./images --dry-run
    python3 db-seeds/upload-product-images.py --user admin --location 4f884711-7c5e-41a7-9aec-338208ef3dba --dir ./images

Uses only the standard library. Asks for the password; nothing is stored. Products that
already have an image are skipped unless --replace is given.
"""
import argparse
import getpass
import json
import mimetypes
import os
import re
import sys
import unicodedata
import urllib.error
import urllib.request
import uuid

IMAGE_EXT = {'.jpg', '.jpeg', '.png', '.webp'}


def norm(name):
    """'S. Pellegrino 0.5 L' -> 'spellegrino05l' — what file names and product names are matched on."""
    s = unicodedata.normalize('NFKD', name)
    s = ''.join(c for c in s if not unicodedata.combining(c)).lower()
    s = s.replace('&', 'and')
    return re.sub(r'[^a-z0-9]', '', s)


def call(api, method, path, token=None, body=None, content_type=None):
    req = urllib.request.Request(api + path, method=method, data=body)
    if token:
        req.add_header('Authorization', 'Bearer ' + token)
    if content_type:
        req.add_header('Content-Type', content_type)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            raw = r.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        sys.exit(f'{method} {path} failed: HTTP {e.code} {e.read().decode(errors="replace")[:300]}')


def multipart(field, filename, data):
    boundary = uuid.uuid4().hex
    ctype = mimetypes.guess_type(filename)[0] or 'application/octet-stream'
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"; filename="{filename}"\r\n'
            f'Content-Type: {ctype}\r\n\r\n').encode() + data + f'\r\n--{boundary}--\r\n'.encode()
    return body, f'multipart/form-data; boundary={boundary}'


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--api', default='https://api.yammer.ro', help='API base URL (default: prod)')
    ap.add_argument('--user', required=True, help='backoffice username')
    ap.add_argument('--location', required=True, help='location id whose products get the images')
    ap.add_argument('--dir', required=True, help='folder with the image files')
    ap.add_argument('--replace', action='store_true', help='also replace images products already have')
    ap.add_argument('--dry-run', action='store_true', help='only show what would be uploaded')
    args = ap.parse_args()

    files = {}
    for f in sorted(os.listdir(args.dir)):
        stem, ext = os.path.splitext(f)
        if ext.lower() in IMAGE_EXT:
            files[norm(stem)] = os.path.join(args.dir, f)
    if not files:
        sys.exit(f'No images in {args.dir}')

    password = getpass.getpass(f'Password for {args.user}: ')
    token = call(args.api, 'POST', '/auth/login',
                 body=json.dumps({'username': args.user, 'password': password}).encode(),
                 content_type='application/json')['token']
    products = call(args.api, 'GET', f'/products?locationId={args.location}', token)

    matched, skipped = [], []
    for p in products:
        path = files.pop(norm(p['name']), None)
        if not path:
            continue
        if p.get('imageObject') and not args.replace:
            skipped.append(p['name'])
            continue
        matched.append((p, path))

    for p, path in matched:
        print(f'{"would upload" if args.dry_run else "uploading"}: {p["name"]}  <-  {os.path.basename(path)}')
        if args.dry_run:
            continue
        with open(path, 'rb') as fh:
            body, ctype = multipart('file', os.path.basename(path), fh.read())
        obj = call(args.api, 'POST', '/menu/image', token, body, ctype)['object']
        call(args.api, 'PUT', f'/products/{p["id"]}', token,
             json.dumps({'locationId': p['locationId'], 'name': p['name'], 'description': p.get('description'),
                         'vatTypeId': p.get('vatTypeId'), 'imageObject': obj}).encode(),
             'application/json')

    print(f'\n{len(matched)} product(s) {"matched" if args.dry_run else "updated"}.')
    if skipped:
        print(f'{len(skipped)} already had an image (use --replace): ' + ', '.join(skipped))
    if files:
        print(f'{len(files)} file(s) matched no product: ' + ', '.join(os.path.basename(v) for v in files.values()))
    without = [p['name'] for p in products if not p.get('imageObject') and not any(p is m[0] for m in matched)]
    if without:
        print(f'{len(without)} product(s) still without an image: ' + ', '.join(without))


if __name__ == '__main__':
    main()
