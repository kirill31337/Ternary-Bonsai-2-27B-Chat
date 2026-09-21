#include <stdio.h>
int __android_log_write(int priority,const char *tag,const char *text) {
    (void)priority;fprintf(stderr,"%s: %s\n",tag,text);return 0;
}
