.class public Lcom/prismml/bonsailocal/repair/MainActivity;
.super Landroid/app/Activity;


# static fields
.field private static loaded:Z


# instance fields
.field private page:I

.field private web:Landroid/webkit/WebView;


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Landroid/app/Activity;-><init>()V

    return-void
.end method

.method private button(Landroid/widget/LinearLayout;Ljava/lang/String;I)V
    .locals 2

    new-instance v0, Landroid/widget/Button;

    invoke-direct {v0, p0}, Landroid/widget/Button;-><init>(Landroid/content/Context;)V

    invoke-virtual {v0, p2}, Landroid/widget/Button;->setText(Ljava/lang/CharSequence;)V

    const/4 v1, 0x0

    invoke-virtual {v0, v1}, Landroid/widget/Button;->setAllCaps(Z)V

    new-instance v1, Lcom/prismml/bonsailocal/repair/Click;

    invoke-direct {v1, p0, p3}, Lcom/prismml/bonsailocal/repair/Click;-><init>(Lcom/prismml/bonsailocal/repair/MainActivity;I)V

    invoke-virtual {v0, v1}, Landroid/widget/Button;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    invoke-virtual {p1, v0}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V

    return-void
.end method

.method private destroyWeb()V
    .locals 1
    invoke-static {p0}, Lcom/prismml/bonsailocal/repair/UiSession;->detach(Lcom/prismml/bonsailocal/repair/MainActivity;)V
    const/4 v0, 0x0
    iput-object v0, p0, Lcom/prismml/bonsailocal/repair/MainActivity;->web:Landroid/webkit/WebView;
    return-void
.end method

.method private layout()Landroid/widget/LinearLayout;
    .locals 3

    new-instance v0, Landroid/widget/LinearLayout;

    invoke-direct {v0, p0}, Landroid/widget/LinearLayout;-><init>(Landroid/content/Context;)V

    const/4 v1, 0x1

    invoke-virtual {v0, v1}, Landroid/widget/LinearLayout;->setOrientation(I)V

    const/16 v1, 0x30

    invoke-virtual {v0, v1, v1, v1, v1}, Landroid/widget/LinearLayout;->setPadding(IIII)V

    new-instance v1, Landroid/widget/ScrollView;

    invoke-direct {v1, p0}, Landroid/widget/ScrollView;-><init>(Landroid/content/Context;)V

    invoke-virtual {v1, v0}, Landroid/widget/ScrollView;->addView(Landroid/view/View;)V

    invoke-virtual {p0, v1}, Landroid/app/Activity;->setContentView(Landroid/view/View;)V

    return-object v0
.end method

.method private text(Landroid/widget/LinearLayout;Ljava/lang/String;)V
    .locals 3

    new-instance v0, Landroid/widget/TextView;

    invoke-direct {v0, p0}, Landroid/widget/TextView;-><init>(Landroid/content/Context;)V

    invoke-virtual {v0, p2}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    const/high16 v1, 0x41800000    # 16.0f

    invoke-virtual {v0, v1}, Landroid/widget/TextView;->setTextSize(F)V

    const/4 v1, 0x1

    invoke-virtual {v0, v1}, Landroid/widget/TextView;->setTextIsSelectable(Z)V

    const/4 v1, 0x0

    const/16 v2, 0x20

    invoke-virtual {v0, v1, v2, v1, v2}, Landroid/widget/TextView;->setPadding(IIII)V

    invoke-virtual {p1, v0}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V

    return-void
.end method


# virtual methods
.method public diagnosticText()Ljava/lang/String;
    .locals 3

    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "Bonsai Local 1.0.0 recovery\nPackage: com.prismml.bonsailocal.repair\nRuntime: PrismML 9a9394a\nDevice: "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    sget-object v1, Landroid/os/Build;->MANUFACTURER:Ljava/lang/String;

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, " "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    sget-object v1, Landroid/os/Build;->MODEL:Ljava/lang/String;

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, "\nAndroid SDK: "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    sget v1, Landroid/os/Build$VERSION;->SDK_INT:I

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v1, "\nABIs: "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    sget-object v1, Landroid/os/Build;->SUPPORTED_ABIS:[Ljava/lang/String;

    invoke-static {v1}, Ljava/util/Arrays;->toString([Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, "\n\n--- UI error ---\n"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, "ui-crash.log"

    invoke-static {p0, v1}, Lcom/prismml/bonsailocal/repair/Report;->readTail(Landroid/content/Context;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, "\n\n--- Native lifecycle ---\n"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, "native.log"

    invoke-static {p0, v1}, Lcom/prismml/bonsailocal/repair/Report;->readTail(Landroid/content/Context;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, "\n\n--- llama-server ---\n"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, "server.log"

    invoke-static {p0, v1}, Lcom/prismml/bonsailocal/repair/Report;->readTail(Landroid/content/Context;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, "\n\n--- Transfers 0.5 ---\n"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-static {}, Lcom/prismml/bonsailocal/repair/Transfers;->json()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, "transfer.log"

    invoke-static {p0, v1}, Lcom/prismml/bonsailocal/repair/Report;->readTail(Landroid/content/Context;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, "\n\n--- Runtime options ---\n"
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    const-string v1, "runtime-options.properties"
    invoke-static {p0, v1}, Lcom/prismml/bonsailocal/repair/Report;->readTail(Landroid/content/Context;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v1
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    const-string v1, "\n\n--- Benchmark results (synthetic) ---\n"
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    const-string v1, "benchmarks.jsonl"
    invoke-static {p0, v1}, Lcom/prismml/bonsailocal/repair/Report;->readTail(Landroid/content/Context;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v1
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method

.method public home()V
    .locals 0
    invoke-virtual {p0}, Lcom/prismml/bonsailocal/repair/MainActivity;->openBonsai()V
    return-void
.end method

.method protected onActivityResult(IILandroid/content/Intent;)V
    .locals 4

    invoke-static {p0, p1, p2, p3}, Lcom/prismml/bonsailocal/repair/BonsaiChromeClient;->result(Landroid/app/Activity;IILandroid/content/Intent;)Z
    move-result v0
    if-eqz v0, :not_chat_attachment
    return-void
    :not_chat_attachment


    invoke-super {p0, p1, p2, p3}, Landroid/app/Activity;->onActivityResult(IILandroid/content/Intent;)V

    const/16 v0, 0x1c87

    if-ne p1, v0, :cond_0

    const/4 v0, -0x1

    if-ne p2, v0, :cond_0

    if-eqz p3, :cond_0

    :try_start_0
    invoke-virtual {p3}, Landroid/content/Intent;->getData()Landroid/net/Uri;

    move-result-object v1

    if-eqz v1, :cond_0
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_1

    :try_start_1
    invoke-virtual {p0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v2

    const/4 v3, 0x1

    invoke-virtual {v2, v1, v3}, Landroid/content/ContentResolver;->takePersistableUriPermission(Landroid/net/Uri;I)V
    :try_end_1
    .catch Ljava/lang/SecurityException; {:try_start_1 .. :try_end_1} :catch_0
    .catch Ljava/lang/Throwable; {:try_start_1 .. :try_end_1} :catch_1

    :try_start_2
    goto :goto_0

    :catch_0
    move-exception v2

    :goto_0
    const-string v2, "com.prismml.bonsailocal.IMPORT"

    invoke-static {p0, v2, v1}, Lcom/prismml/bonsailocal/repair/Transfers;->request(Landroid/content/Context;Ljava/lang/String;Landroid/net/Uri;)V
    :try_end_2
    .catch Ljava/lang/Throwable; {:try_start_2 .. :try_end_2} :catch_1

    goto :goto_1

    :catch_1
    move-exception v0

    invoke-virtual {p0, v0}, Lcom/prismml/bonsailocal/repair/MainActivity;->showError(Ljava/lang/Throwable;)V

    :cond_0
    :goto_1
    return-void
.end method

.method public onBackPressed()V
    .locals 2
    iget v0, p0, Lcom/prismml/bonsailocal/repair/MainActivity;->page:I
    const/4 v1, 0x2
    if-ne v0, v1, :background
    invoke-virtual {p0}, Lcom/prismml/bonsailocal/repair/MainActivity;->openBonsai()V
    return-void
    :background
    invoke-static {p0}, Lcom/prismml/bonsailocal/repair/UiSession;->back(Lcom/prismml/bonsailocal/repair/MainActivity;)Z
    move-result v0
    if-nez v0, :done
    const/4 v0, 0x1
    invoke-virtual {p0, v0}, Landroid/app/Activity;->moveTaskToBack(Z)Z
    :done
    return-void
.end method

.method protected onCreate(Landroid/os/Bundle;)V
    .locals 1

    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V

    :try_start_0
    invoke-virtual {p0}, Lcom/prismml/bonsailocal/repair/MainActivity;->openBonsai()V
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :catch_0
    move-exception v0

    invoke-virtual {p0, v0}, Lcom/prismml/bonsailocal/repair/MainActivity;->showError(Ljava/lang/Throwable;)V

    return-void
.end method

.method protected onDestroy()V
    .locals 0
    invoke-static {p0}, Lcom/prismml/bonsailocal/repair/UiSession;->detach(Lcom/prismml/bonsailocal/repair/MainActivity;)V
    invoke-super {p0}, Landroid/app/Activity;->onDestroy()V
    return-void
.end method

.method public openBonsai()V
    .locals 1
    invoke-static {p0}, Lcom/prismml/bonsailocal/repair/RuntimeEnvironment;->ensure(Landroid/content/Context;)V
    const/4 v0, 0x1
    sput-boolean v0, Lcom/prismml/bonsailocal/repair/MainActivity;->loaded:Z
    invoke-static {p0}, Lcom/prismml/bonsailocal/repair/UiSession;->open(Lcom/prismml/bonsailocal/repair/MainActivity;)Landroid/webkit/WebView;
    move-result-object v0
    iput-object v0, p0, Lcom/prismml/bonsailocal/repair/MainActivity;->web:Landroid/webkit/WebView;
    const/4 v0, 0x1
    iput v0, p0, Lcom/prismml/bonsailocal/repair/MainActivity;->page:I
    invoke-static {p0}, Lcom/prismml/bonsailocal/repair/RuntimeEnvironment;->requestNotifications(Landroid/app/Activity;)V
    return-void
.end method

.method public pickModel()V
    .locals 3

    invoke-static {}, Lcom/prismml/bonsailocal/repair/Transfers;->isBusy()Z

    move-result v0

    if-nez v0, :cond_0

    new-instance v0, Landroid/content/Intent;

    const-string v1, "android.intent.action.OPEN_DOCUMENT"

    invoke-direct {v0, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    const-string v1, "android.intent.category.OPENABLE"

    invoke-virtual {v0, v1}, Landroid/content/Intent;->addCategory(Ljava/lang/String;)Landroid/content/Intent;

    const-string v1, "*/*"

    invoke-virtual {v0, v1}, Landroid/content/Intent;->setType(Ljava/lang/String;)Landroid/content/Intent;

    const/16 v1, 0x41

    invoke-virtual {v0, v1}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    const-string v1, "android.intent.extra.LOCAL_ONLY"

    const/4 v2, 0x1

    invoke-virtual {v0, v1, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;

    const/16 v1, 0x1c87

    invoke-virtual {p0, v0, v1}, Landroid/app/Activity;->startActivityForResult(Landroid/content/Intent;I)V

    :cond_0
    return-void
.end method

.method public shareDiagnostics()V
    .locals 3

    new-instance v0, Landroid/content/Intent;

    const-string v1, "android.intent.action.SEND"

    invoke-direct {v0, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    const-string v1, "text/plain"

    invoke-virtual {v0, v1}, Landroid/content/Intent;->setType(Ljava/lang/String;)Landroid/content/Intent;

    const-string v1, "android.intent.extra.TEXT"

    invoke-virtual {p0}, Lcom/prismml/bonsailocal/repair/MainActivity;->diagnosticText()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    const-string v1, "\u041e\u0442\u043f\u0440\u0430\u0432\u0438\u0442\u044c \u0434\u0438\u0430\u0433\u043d\u043e\u0441\u0442\u0438\u043a\u0443 Bonsai"

    invoke-static {v0, v1}, Landroid/content/Intent;->createChooser(Landroid/content/Intent;Ljava/lang/CharSequence;)Landroid/content/Intent;

    move-result-object v0

    invoke-virtual {p0, v0}, Landroid/app/Activity;->startActivity(Landroid/content/Intent;)V

    return-void
.end method

.method public showDiagnostics()V
    .locals 3

    invoke-direct {p0}, Lcom/prismml/bonsailocal/repair/MainActivity;->layout()Landroid/widget/LinearLayout;

    move-result-object v0

    invoke-direct {p0}, Lcom/prismml/bonsailocal/repair/MainActivity;->destroyWeb()V

    const-string v1, "\u041f\u043e\u0434\u0435\u043b\u0438\u0442\u044c\u0441\u044f \u0434\u0438\u0430\u0433\u043d\u043e\u0441\u0442\u0438\u043a\u043e\u0439"

    const/4 v2, 0x3

    invoke-direct {p0, v0, v1, v2}, Lcom/prismml/bonsailocal/repair/MainActivity;->button(Landroid/widget/LinearLayout;Ljava/lang/String;I)V

    const-string v1, "\u041d\u0430 \u0433\u043b\u0430\u0432\u043d\u044b\u0439 \u044d\u043a\u0440\u0430\u043d"

    const/4 v2, 0x0

    invoke-direct {p0, v0, v1, v2}, Lcom/prismml/bonsailocal/repair/MainActivity;->button(Landroid/widget/LinearLayout;Ljava/lang/String;I)V

    invoke-virtual {p0}, Lcom/prismml/bonsailocal/repair/MainActivity;->diagnosticText()Ljava/lang/String;

    move-result-object v1

    invoke-direct {p0, v0, v1}, Lcom/prismml/bonsailocal/repair/MainActivity;->text(Landroid/widget/LinearLayout;Ljava/lang/String;)V

    const/4 v0, 0x2

    iput v0, p0, Lcom/prismml/bonsailocal/repair/MainActivity;->page:I

    return-void
.end method

.method public showError(Ljava/lang/Throwable;)V
    .locals 3

    invoke-static {p0, p1}, Lcom/prismml/bonsailocal/repair/Report;->record(Landroid/content/Context;Ljava/lang/Throwable;)V

    invoke-direct {p0}, Lcom/prismml/bonsailocal/repair/MainActivity;->layout()Landroid/widget/LinearLayout;

    move-result-object v0

    const-string v1, "\u041e\u0448\u0438\u0431\u043a\u0430 \u043f\u0435\u0440\u0435\u0445\u0432\u0430\u0447\u0435\u043d\u0430. \u041c\u043e\u0434\u0435\u043b\u044c \u043d\u0435 \u0437\u0430\u043f\u0443\u0441\u043a\u0430\u0435\u0442\u0441\u044f \u0430\u0432\u0442\u043e\u043c\u0430\u0442\u0438\u0447\u0435\u0441\u043a\u0438.\n\n"

    invoke-static {p1}, Landroid/util/Log;->getStackTraceString(Ljava/lang/Throwable;)Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v1, v2}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    invoke-direct {p0, v0, v1}, Lcom/prismml/bonsailocal/repair/MainActivity;->text(Landroid/widget/LinearLayout;Ljava/lang/String;)V

    const-string v1, "\u041f\u043e\u0434\u0435\u043b\u0438\u0442\u044c\u0441\u044f \u0434\u0438\u0430\u0433\u043d\u043e\u0441\u0442\u0438\u043a\u043e\u0439"

    const/4 v2, 0x3

    invoke-direct {p0, v0, v1, v2}, Lcom/prismml/bonsailocal/repair/MainActivity;->button(Landroid/widget/LinearLayout;Ljava/lang/String;I)V

    const-string v1, "\u041d\u0430 \u0433\u043b\u0430\u0432\u043d\u044b\u0439 \u044d\u043a\u0440\u0430\u043d"

    const/4 v2, 0x0

    invoke-direct {p0, v0, v1, v2}, Lcom/prismml/bonsailocal/repair/MainActivity;->button(Landroid/widget/LinearLayout;Ljava/lang/String;I)V

    const/4 v0, 0x2

    iput v0, p0, Lcom/prismml/bonsailocal/repair/MainActivity;->page:I

    return-void
.end method
