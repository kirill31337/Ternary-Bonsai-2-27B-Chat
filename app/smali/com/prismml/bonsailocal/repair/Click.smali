.class final Lcom/prismml/bonsailocal/repair/Click;
.super Ljava/lang/Object;

# interfaces
.implements Landroid/view/View$OnClickListener;


# instance fields
.field private final action:I

.field private final activity:Lcom/prismml/bonsailocal/repair/MainActivity;


# direct methods
.method public constructor <init>(Lcom/prismml/bonsailocal/repair/MainActivity;I)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lcom/prismml/bonsailocal/repair/Click;->activity:Lcom/prismml/bonsailocal/repair/MainActivity;

    iput p2, p0, Lcom/prismml/bonsailocal/repair/Click;->action:I

    return-void
.end method


# virtual methods
.method public onClick(Landroid/view/View;)V
    .locals 2

    iget-object v0, p0, Lcom/prismml/bonsailocal/repair/Click;->activity:Lcom/prismml/bonsailocal/repair/MainActivity;

    iget v1, p0, Lcom/prismml/bonsailocal/repair/Click;->action:I

    :try_start_0
    packed-switch v1, :pswitch_data_0

    invoke-virtual {v0}, Lcom/prismml/bonsailocal/repair/MainActivity;->home()V

    goto :goto_0

    :pswitch_0
    invoke-virtual {v0}, Lcom/prismml/bonsailocal/repair/MainActivity;->openBonsai()V

    goto :goto_0

    :pswitch_1
    invoke-virtual {v0}, Lcom/prismml/bonsailocal/repair/MainActivity;->showDiagnostics()V

    goto :goto_0

    :pswitch_2
    invoke-virtual {v0}, Lcom/prismml/bonsailocal/repair/MainActivity;->shareDiagnostics()V
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    :goto_0
    return-void

    :catch_0
    move-exception v1

    invoke-virtual {v0, v1}, Lcom/prismml/bonsailocal/repair/MainActivity;->showError(Ljava/lang/Throwable;)V

    return-void

    :pswitch_data_0
    .packed-switch 0x1
        :pswitch_0
        :pswitch_1
        :pswitch_2
    .end packed-switch
.end method
