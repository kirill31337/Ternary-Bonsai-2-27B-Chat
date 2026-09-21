#pragma once
// Small ABI declarations for the no-NDK build. Android libc/liblog are linked
// explicitly through import-only stubs; the stubs are NEVER packaged in the APK.
// An NDK build instead uses the platform's real headers and API stub libraries.
#ifdef BONSAI_NDK
#include <pthread.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <time.h>
#include <android/log.h>
#else
#include <stddef.h>
#include <stdint.h>
typedef long ssize_t;
typedef long off_t;
typedef unsigned long pthread_t;
extern int pthread_create(pthread_t*, const void*, void* (*)(void*), void*);
extern int pthread_join(pthread_t, void**);
extern int usleep(unsigned int);
extern int open(const char*, int, ...);
extern int close(int);
extern ssize_t read(int, void*, size_t);
extern ssize_t write(int, const void*, size_t);
extern off_t lseek(int, off_t, int);
extern int unlink(const char*);
extern int snprintf(char*, size_t, const char*, ...);
extern size_t strlen(const char*);
extern void *memcpy(void*, const void*, size_t);
extern void *memset(void*, int, size_t);
extern int memcmp(const void*,const void*,size_t);
extern long long strtoll(const char*,char**,int);
extern int getpagesize(void);
extern int __android_log_write(int,const char*,const char*);
#define O_RDONLY 0
#define O_WRONLY 1
#define O_CREAT 64
#define O_TRUNC 512
#define O_APPEND 1024
#define SEEK_SET 0
#define SEEK_END 2
#endif
