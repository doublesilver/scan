package com.scan.warehouse.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@SuppressWarnings({"unchecked", "deprecation"})
public final class ProductDao_Impl implements ProductDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<CachedProduct> __insertionAdapterOfCachedProduct;

  public ProductDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfCachedProduct = new EntityInsertionAdapter<CachedProduct>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `cached_products` (`barcode`,`skuId`,`productName`,`categoryName`,`brandName`,`imageUrls`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CachedProduct entity) {
        if (entity.getBarcode() == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.getBarcode());
        }
        statement.bindLong(2, entity.getSkuId());
        if (entity.getProductName() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getProductName());
        }
        if (entity.getCategoryName() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getCategoryName());
        }
        if (entity.getBrandName() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getBrandName());
        }
        if (entity.getImageUrls() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getImageUrls());
        }
      }
    };
  }

  @Override
  public Object insertAll(final List<CachedProduct> products,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfCachedProduct.insert(products);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object getByBarcode(final String barcode,
      final Continuation<? super CachedProduct> $completion) {
    final String _sql = "SELECT * FROM cached_products WHERE barcode = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (barcode == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, barcode);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<CachedProduct>() {
      @Override
      @Nullable
      public CachedProduct call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfBarcode = CursorUtil.getColumnIndexOrThrow(_cursor, "barcode");
          final int _cursorIndexOfSkuId = CursorUtil.getColumnIndexOrThrow(_cursor, "skuId");
          final int _cursorIndexOfProductName = CursorUtil.getColumnIndexOrThrow(_cursor, "productName");
          final int _cursorIndexOfCategoryName = CursorUtil.getColumnIndexOrThrow(_cursor, "categoryName");
          final int _cursorIndexOfBrandName = CursorUtil.getColumnIndexOrThrow(_cursor, "brandName");
          final int _cursorIndexOfImageUrls = CursorUtil.getColumnIndexOrThrow(_cursor, "imageUrls");
          final CachedProduct _result;
          if (_cursor.moveToFirst()) {
            final String _tmpBarcode;
            if (_cursor.isNull(_cursorIndexOfBarcode)) {
              _tmpBarcode = null;
            } else {
              _tmpBarcode = _cursor.getString(_cursorIndexOfBarcode);
            }
            final int _tmpSkuId;
            _tmpSkuId = _cursor.getInt(_cursorIndexOfSkuId);
            final String _tmpProductName;
            if (_cursor.isNull(_cursorIndexOfProductName)) {
              _tmpProductName = null;
            } else {
              _tmpProductName = _cursor.getString(_cursorIndexOfProductName);
            }
            final String _tmpCategoryName;
            if (_cursor.isNull(_cursorIndexOfCategoryName)) {
              _tmpCategoryName = null;
            } else {
              _tmpCategoryName = _cursor.getString(_cursorIndexOfCategoryName);
            }
            final String _tmpBrandName;
            if (_cursor.isNull(_cursorIndexOfBrandName)) {
              _tmpBrandName = null;
            } else {
              _tmpBrandName = _cursor.getString(_cursorIndexOfBrandName);
            }
            final String _tmpImageUrls;
            if (_cursor.isNull(_cursorIndexOfImageUrls)) {
              _tmpImageUrls = null;
            } else {
              _tmpImageUrls = _cursor.getString(_cursorIndexOfImageUrls);
            }
            _result = new CachedProduct(_tmpBarcode,_tmpSkuId,_tmpProductName,_tmpCategoryName,_tmpBrandName,_tmpImageUrls);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object searchByName(final String query,
      final Continuation<? super List<CachedProduct>> $completion) {
    final String _sql = "SELECT * FROM cached_products WHERE productName LIKE '%' || ? || '%'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<CachedProduct>>() {
      @Override
      @NonNull
      public List<CachedProduct> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfBarcode = CursorUtil.getColumnIndexOrThrow(_cursor, "barcode");
          final int _cursorIndexOfSkuId = CursorUtil.getColumnIndexOrThrow(_cursor, "skuId");
          final int _cursorIndexOfProductName = CursorUtil.getColumnIndexOrThrow(_cursor, "productName");
          final int _cursorIndexOfCategoryName = CursorUtil.getColumnIndexOrThrow(_cursor, "categoryName");
          final int _cursorIndexOfBrandName = CursorUtil.getColumnIndexOrThrow(_cursor, "brandName");
          final int _cursorIndexOfImageUrls = CursorUtil.getColumnIndexOrThrow(_cursor, "imageUrls");
          final List<CachedProduct> _result = new ArrayList<CachedProduct>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CachedProduct _item;
            final String _tmpBarcode;
            if (_cursor.isNull(_cursorIndexOfBarcode)) {
              _tmpBarcode = null;
            } else {
              _tmpBarcode = _cursor.getString(_cursorIndexOfBarcode);
            }
            final int _tmpSkuId;
            _tmpSkuId = _cursor.getInt(_cursorIndexOfSkuId);
            final String _tmpProductName;
            if (_cursor.isNull(_cursorIndexOfProductName)) {
              _tmpProductName = null;
            } else {
              _tmpProductName = _cursor.getString(_cursorIndexOfProductName);
            }
            final String _tmpCategoryName;
            if (_cursor.isNull(_cursorIndexOfCategoryName)) {
              _tmpCategoryName = null;
            } else {
              _tmpCategoryName = _cursor.getString(_cursorIndexOfCategoryName);
            }
            final String _tmpBrandName;
            if (_cursor.isNull(_cursorIndexOfBrandName)) {
              _tmpBrandName = null;
            } else {
              _tmpBrandName = _cursor.getString(_cursorIndexOfBrandName);
            }
            final String _tmpImageUrls;
            if (_cursor.isNull(_cursorIndexOfImageUrls)) {
              _tmpImageUrls = null;
            } else {
              _tmpImageUrls = _cursor.getString(_cursorIndexOfImageUrls);
            }
            _item = new CachedProduct(_tmpBarcode,_tmpSkuId,_tmpProductName,_tmpCategoryName,_tmpBrandName,_tmpImageUrls);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM cached_products";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final Integer _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
