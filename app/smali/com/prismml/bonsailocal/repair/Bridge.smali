.class public final Lcom/prismml/bonsailocal/repair/Bridge;
.super Ljava/lang/Object;


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static native init(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
.end method

.method public static native shutdown()V
.end method


# virtual methods
.method public native configureOptions(IIZIII)Z
.end method

.method public native configureRuntime(IIZIIII)Z
.end method

.method public native selfTest()V
    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation
.end method

.method public native start()V
    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation
.end method

.method public native status()Ljava/lang/String;
    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation
.end method

.method public native stop()V
    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation
.end method

.method public native configureVision(Z)Z
.end method
