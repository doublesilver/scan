package com.scan.warehouse.scanner;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000B\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u0019J\u000e\u0010\u001a\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u0019J\u000e\u0010\u001b\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u0019R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u0014\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00040\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0011X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00040\u0013\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015\u00a8\u0006\u001c"}, d2 = {"Lcom/scan/warehouse/scanner/DataWedgeManager;", "", "()V", "ACTION_DATAWEDGE", "", "ACTION_SCAN", "BARCODE_PATTERN", "EXTRA_CREATE_PROFILE", "EXTRA_DATA", "EXTRA_SET_CONFIG", "_scanFlow", "Lkotlinx/coroutines/flow/MutableSharedFlow;", "barcodeRegex", "Lkotlin/text/Regex;", "isRegistered", "", "receiver", "Landroid/content/BroadcastReceiver;", "scanFlow", "Lkotlinx/coroutines/flow/SharedFlow;", "getScanFlow", "()Lkotlinx/coroutines/flow/SharedFlow;", "register", "", "context", "Landroid/content/Context;", "setupProfile", "unregister", "app_liveDebug"})
public final class DataWedgeManager {
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ACTION_SCAN = "com.scan.warehouse.SCAN";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_DATA = "com.symbol.datawedge.data_string";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String ACTION_DATAWEDGE = "com.symbol.datawedge.api.ACTION";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String EXTRA_CREATE_PROFILE = "com.symbol.datawedge.api.CREATE_PROFILE";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String EXTRA_SET_CONFIG = "com.symbol.datawedge.api.SET_CONFIG";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String BARCODE_PATTERN = "^\\d{8,13}$";
    @org.jetbrains.annotations.NotNull()
    private static final kotlin.text.Regex barcodeRegex = null;
    private static boolean isRegistered = false;
    @org.jetbrains.annotations.NotNull()
    private static final kotlinx.coroutines.flow.MutableSharedFlow<java.lang.String> _scanFlow = null;
    @org.jetbrains.annotations.NotNull()
    private static final kotlinx.coroutines.flow.SharedFlow<java.lang.String> scanFlow = null;
    @org.jetbrains.annotations.NotNull()
    private static final android.content.BroadcastReceiver receiver = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.scan.warehouse.scanner.DataWedgeManager INSTANCE = null;
    
    private DataWedgeManager() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.SharedFlow<java.lang.String> getScanFlow() {
        return null;
    }
    
    public final void register(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
    }
    
    public final void unregister(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
    }
    
    public final void setupProfile(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
    }
}