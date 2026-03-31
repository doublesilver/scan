"""Usage: python -m app.services.url_import_cli [path_to_excel]"""
import asyncio
import sys

from app.db.database import get_db
from app.services.url_import_service import import_purchase_urls


async def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "./data/xlsx/purchase_urls.xlsx"
    db = await get_db()
    result = await import_purchase_urls(db, path)
    print(result)


if __name__ == "__main__":
    asyncio.run(main())
