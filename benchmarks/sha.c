/*
 * SHA-256 Hashing Implementation for MSP430
 * Based on the cputest.c benchmark structure
 */

#include "msp430setup.h"
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#if defined(__GNUC__) && (__GNUC__ >= 9)
#include <msp430.h>
#include <stdarg.h>
#elif __MSPGCC__
#include <msp430.h>
#include <legacymsp430.h>
#define eint() __eint()
#define dint() __dint()
#else /* __MSPGCC__ */
#include <signal.h>
#include <io.h>
#endif /* __MSPGCC__ */

/* Define test and assertion macros */
#define TEST(...) if(__VA_ARGS__) {					 \
                    printf("OK: " #__VA_ARGS__ " passed at %s:%d\n", __FILE__,__LINE__); \
                  } else {						 \
                    printf("FAIL: " #__VA_ARGS__ " failed at %s:%d\n", __FILE__,__LINE__); \
                  }

#define assertTrue(...) TEST(__VA_ARGS__)
#define assertFalse(...) TEST(!(__VA_ARGS__))

/****************************** MACROS ******************************/
#define SHA256_BLOCK_SIZE 32            // SHA256 outputs a 32 byte digest

/* SHA-256 specific macros */
#define ROTLEFT(a,b) (((a) << (b)) | ((a) >> (32-(b))))
#define ROTRIGHT(a,b) (((a) >> (b)) | ((a) << (32-(b))))

#define CH(x,y,z) (((x) & (y)) ^ (~(x) & (z)))
#define MAJ(x,y,z) (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))
#define EP0(x) (ROTRIGHT(x,2) ^ ROTRIGHT(x,13) ^ ROTRIGHT(x,22))
#define EP1(x) (ROTRIGHT(x,6) ^ ROTRIGHT(x,11) ^ ROTRIGHT(x,25))
#define SIG0(x) (ROTRIGHT(x,7) ^ ROTRIGHT(x,18) ^ ((x) >> 3))
#define SIG1(x) (ROTRIGHT(x,17) ^ ROTRIGHT(x,19) ^ ((x) >> 10))

/**************************** DATA TYPES ****************************/
typedef unsigned char BYTE;             // 8-bit byte
typedef unsigned int  WORD;             // 32-bit word

typedef struct {
    BYTE data[64];
    WORD datalen;
    unsigned long long bitlen;
    WORD state[8];
} SHA256_CTX;

/* Benchmark parameters */
#define SMALL_INPUT_SIZE 64    /* Small input test - 64 bytes */
#define MEDIUM_INPUT_SIZE 256  /* Medium input - 256 bytes */
#define LARGE_INPUT_SIZE 512   /* Large input - 512 bytes */

/**************************** VARIABLES *****************************/
static const WORD k[64] = {
        0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
        0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
        0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
        0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
        0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
        0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
        0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
        0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
};

/*********************** FUNCTION DECLARATIONS **********************/
void sha256_transform(SHA256_CTX *ctx, const BYTE data[]);
void sha256_init(SHA256_CTX *ctx);
void sha256_update(SHA256_CTX *ctx, const BYTE data[], size_t len);
void sha256_final(SHA256_CTX *ctx, BYTE hash[]);
void print_hash(BYTE hash[]);
int verify_hash(BYTE hash1[], BYTE hash2[]);

/*********************** FUNCTION DEFINITIONS ***********************/
void sha256_transform(SHA256_CTX *ctx, const BYTE data[])
{
    WORD a, b, c, d, e, f, g, h, i, j, t1, t2, m[64];

    for (i = 0, j = 0; i < 16; ++i, j += 4)
        m[i] = (data[j] << 24) | (data[j + 1] << 16) | (data[j + 2] << 8) | (data[j + 3]);
    for ( ; i < 64; ++i)
        m[i] = SIG1(m[i - 2]) + m[i - 7] + SIG0(m[i - 15]) + m[i - 16];

    a = ctx->state[0];
    b = ctx->state[1];
    c = ctx->state[2];
    d = ctx->state[3];
    e = ctx->state[4];
    f = ctx->state[5];
    g = ctx->state[6];
    h = ctx->state[7];

    for (i = 0; i < 64; ++i) {
        t1 = h + EP1(e) + CH(e,f,g) + k[i] + m[i];
        t2 = EP0(a) + MAJ(a,b,c);
        h = g;
        g = f;
        f = e;
        e = d + t1;
        d = c;
        c = b;
        b = a;
        a = t1 + t2;
    }

    ctx->state[0] += a;
    ctx->state[1] += b;
    ctx->state[2] += c;
    ctx->state[3] += d;
    ctx->state[4] += e;
    ctx->state[5] += f;
    ctx->state[6] += g;
    ctx->state[7] += h;
}

void sha256_init(SHA256_CTX *ctx)
{
    ctx->datalen = 0;
    ctx->bitlen = 0;
    ctx->state[0] = 0x6a09e667;
    ctx->state[1] = 0xbb67ae85;
    ctx->state[2] = 0x3c6ef372;
    ctx->state[3] = 0xa54ff53a;
    ctx->state[4] = 0x510e527f;
    ctx->state[5] = 0x9b05688c;
    ctx->state[6] = 0x1f83d9ab;
    ctx->state[7] = 0x5be0cd19;
}

void sha256_update(SHA256_CTX *ctx, const BYTE data[], size_t len)
{
    WORD i;

    for (i = 0; i < len; ++i) {
        ctx->data[ctx->datalen] = data[i];
        ctx->datalen++;
        if (ctx->datalen == 64) {
            sha256_transform(ctx, ctx->data);
            ctx->bitlen += 512;
            ctx->datalen = 0;
        }
    }
}

void sha256_final(SHA256_CTX *ctx, BYTE hash[])
{
    WORD i;

    i = ctx->datalen;

    // Pad whatever data is left in the buffer.
    if (ctx->datalen < 56) {
        ctx->data[i++] = 0x80;
        while (i < 56)
            ctx->data[i++] = 0x00;
    }
    else {
        ctx->data[i++] = 0x80;
        while (i < 64)
            ctx->data[i++] = 0x00;
        sha256_transform(ctx, ctx->data);
        memset(ctx->data, 0, 56);
    }

    // Append to the padding the total message's length in bits and transform.
    ctx->bitlen += ctx->datalen * 8;
    ctx->data[63] = ctx->bitlen;
    ctx->data[62] = ctx->bitlen >> 8;
    ctx->data[61] = ctx->bitlen >> 16;
    ctx->data[60] = ctx->bitlen >> 24;
    ctx->data[59] = ctx->bitlen >> 32;
    ctx->data[58] = ctx->bitlen >> 40;
    ctx->data[57] = ctx->bitlen >> 48;
    ctx->data[56] = ctx->bitlen >> 56;
    sha256_transform(ctx, ctx->data);

    // Since this implementation uses little endian byte ordering and SHA uses big endian,
    // reverse all the bytes when copying the final state to the output hash.
    for (i = 0; i < 4; ++i) {
        hash[i]      = (ctx->state[0] >> (24 - i * 8)) & 0x000000ff;
        hash[i + 4]  = (ctx->state[1] >> (24 - i * 8)) & 0x000000ff;
        hash[i + 8]  = (ctx->state[2] >> (24 - i * 8)) & 0x000000ff;
        hash[i + 12] = (ctx->state[3] >> (24 - i * 8)) & 0x000000ff;
        hash[i + 16] = (ctx->state[4] >> (24 - i * 8)) & 0x000000ff;
        hash[i + 20] = (ctx->state[5] >> (24 - i * 8)) & 0x000000ff;
        hash[i + 24] = (ctx->state[6] >> (24 - i * 8)) & 0x000000ff;
        hash[i + 28] = (ctx->state[7] >> (24 - i * 8)) & 0x000000ff;
    }
}

/* Print hash in hex format */
void print_hash(BYTE hash[])
{
    int i;
    for(i = 0; i < SHA256_BLOCK_SIZE; i++) {
        printf("%02x", hash[i]);
    }
    printf("\n");
}

/* Compare if two hashes are equal */
int verify_hash(BYTE hash1[], BYTE hash2[])
{
    int i;
    for(i = 0; i < SHA256_BLOCK_SIZE; i++) {
        if(hash1[i] != hash2[i]) {
            return 0; /* Hashes differ */
        }
    }
    return 1; /* Hashes are identical */
}

/* Generate test data of specified size */
void generate_test_data(BYTE* buffer, size_t size, int seed)
{
    int i;
    unsigned int next = seed;
    
    for(i = 0; i < size; i++) {
        /* Simple linear congruential generator */
        next = (next * 1103515245 + 12345) & 0x7FFFFFFF;
        buffer[i] = next % 256; /* Values 0-255 */
    }
}

/* Test cases and benchmarking */
static int caseID = 0;

static void initTest() {
    caseID = 0;
}

static void testCase(char *description) {
    caseID++;
    printf("-------------\n");
    printf("TEST %d: %s\n", caseID, description);
}

/* Test SHA-256 with simple inputs */
static void testSimpleSHA256()
{
    SHA256_CTX ctx;
    BYTE hash[SHA256_BLOCK_SIZE];
    const char* test_string = "abc"; /* Simple test case */
    
    testCase("Simple SHA-256 Test");
    
    /* Test with "abc" - known hash value */
    printf("Testing SHA-256 with input: \"%s\"\n", test_string);
    
    sha256_init(&ctx);
    sha256_update(&ctx, (BYTE*)test_string, strlen(test_string));
    sha256_final(&ctx, hash);
    
    printf("Generated hash: ");
    print_hash(hash);
    
    /* Expected hash for "abc": 
     * ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad */
    BYTE expected_hash[SHA256_BLOCK_SIZE] = {
        0xba, 0x78, 0x16, 0xbf, 0x8f, 0x01, 0xcf, 0xea,
        0x41, 0x41, 0x40, 0xde, 0x5d, 0xae, 0x22, 0x23,
        0xb0, 0x03, 0x61, 0xa3, 0x96, 0x17, 0x7a, 0x9c,
        0xb4, 0x10, 0xff, 0x61, 0xf2, 0x00, 0x15, 0xad
    };
    
    printf("Expected hash: ");
    print_hash(expected_hash);
    
    /* Verify the hash matches expected value */
    if (verify_hash(hash, expected_hash)) {
        printf("Hash verification successful!\n");
    } else {
        printf("Hash verification failed!\n");
    }
    
    assertTrue(verify_hash(hash, expected_hash));
}

/* Test SHA-256 with various input sizes */
static void testVariousInputSizes()
{
    SHA256_CTX ctx;
    BYTE hash1[SHA256_BLOCK_SIZE], hash2[SHA256_BLOCK_SIZE];
    BYTE* data;
    int i;
    
    testCase("SHA-256 with Various Input Sizes");
    
    /* Test with empty string */
    printf("Testing SHA-256 with empty string\n");
    sha256_init(&ctx);
    sha256_final(&ctx, hash1);
    printf("Hash of empty string: ");
    print_hash(hash1);
    
    /* Test with small input (single block) */
    data = (BYTE*)malloc(64);
    generate_test_data(data, 64, 12345);
    
    printf("Testing SHA-256 with 64-byte input\n");
    sha256_init(&ctx);
    sha256_update(&ctx, data, 64);
    sha256_final(&ctx, hash1);
    printf("Hash of 64-byte input: ");
    print_hash(hash1);
    
    /* Test with multi-block input */
    free(data);
    data = (BYTE*)malloc(128);
    generate_test_data(data, 128, 12345);
    
    printf("Testing SHA-256 with 128-byte input\n");
    sha256_init(&ctx);
    sha256_update(&ctx, data, 128);
    sha256_final(&ctx, hash1);
    printf("Hash of 128-byte input: ");
    print_hash(hash1);
    
    /* Verify incremental update produces same result as one-shot update */
    printf("Testing incremental vs one-shot hashing\n");
    
    /* One-shot hash */
    sha256_init(&ctx);
    sha256_update(&ctx, data, 128);
    sha256_final(&ctx, hash1);
    
    /* Incremental hash */
    sha256_init(&ctx);
    sha256_update(&ctx, data, 64);      /* First half */
    sha256_update(&ctx, data + 64, 64); /* Second half */
    sha256_final(&ctx, hash2);
    
    if (verify_hash(hash1, hash2)) {
        printf("Incremental hashing verified: both methods produce identical hashes\n");
    } else {
        printf("Incremental hashing failed: hashes differ\n");
    }
    
    assertTrue(verify_hash(hash1, hash2));
    
    free(data);
}

/* Benchmark SHA-256 performance with different input sizes */
static void benchmarkSHA256()
{
    SHA256_CTX ctx;
    BYTE hash[SHA256_BLOCK_SIZE];
    BYTE* small_input;
    BYTE* medium_input;
    BYTE* large_input;
    unsigned int startTime, endTime, smallTime, mediumTime, largeTime;
    int i, iterations = 10;
    
    testCase("SHA-256 Performance Benchmark");
    
    /* Allocate and initialize test data */
    small_input = (BYTE*)malloc(SMALL_INPUT_SIZE);
    medium_input = (BYTE*)malloc(MEDIUM_INPUT_SIZE);
    large_input = (BYTE*)malloc(LARGE_INPUT_SIZE);
    
    generate_test_data(small_input, SMALL_INPUT_SIZE, 12345);
    generate_test_data(medium_input, MEDIUM_INPUT_SIZE, 67890);
    generate_test_data(large_input, LARGE_INPUT_SIZE, 24680);
    
    /* Setup timer */
    dint();
    /* Select ACLK 32768Hz clock */
    TBCTL = TBSSEL0 | TBCLR | ID_0;
    TBCTL |= MC1; /* Start Timer_B in continuous mode */
    TBR = 0;
    
    /* Benchmark small input */
    printf("Benchmarking with %d-byte input (%d iterations)...\n", SMALL_INPUT_SIZE, iterations);
    startTime = TBR;
    
    for (i = 0; i < iterations; i++) {
        sha256_init(&ctx);
        sha256_update(&ctx, small_input, SMALL_INPUT_SIZE);
        sha256_final(&ctx, hash);
    }
    
    endTime = TBR;
    smallTime = endTime - startTime;
    
    /* Benchmark medium input */
    printf("Benchmarking with %d-byte input (%d iterations)...\n", MEDIUM_INPUT_SIZE, iterations);
    startTime = TBR;
    
    for (i = 0; i < iterations; i++) {
        sha256_init(&ctx);
        sha256_update(&ctx, medium_input, MEDIUM_INPUT_SIZE);
        sha256_final(&ctx, hash);
    }
    
    endTime = TBR;
    mediumTime = endTime - startTime;
    
    /* Benchmark large input */
    printf("Benchmarking with %d-byte input (%d iterations)...\n", LARGE_INPUT_SIZE, iterations);
    startTime = TBR;
    
    for (i = 0; i < iterations; i++) {
        sha256_init(&ctx);
        sha256_update(&ctx, large_input, LARGE_INPUT_SIZE);
        sha256_final(&ctx, hash);
    }
    
    endTime = TBR;
    largeTime = endTime - startTime;
    
    /* Print benchmark results */
    printf("\nSHA-256 Performance Results:\n");
    printf("%d-byte input: %u timer ticks (avg: %u ticks per hash)\n", 
           SMALL_INPUT_SIZE, smallTime, smallTime/iterations);
    printf("%d-byte input: %u timer ticks (avg: %u ticks per hash)\n", 
           MEDIUM_INPUT_SIZE, mediumTime, mediumTime/iterations);
    printf("%d-byte input: %u timer ticks (avg: %u ticks per hash)\n", 
           LARGE_INPUT_SIZE, largeTime, largeTime/iterations);
    
    /* Calculate throughput (bytes per tick) */
    float small_throughput = (float)(SMALL_INPUT_SIZE * iterations) / smallTime;
    float medium_throughput = (float)(MEDIUM_INPUT_SIZE * iterations) / mediumTime;
    float large_throughput = (float)(LARGE_INPUT_SIZE * iterations) / largeTime;
    
    printf("\nThroughput:\n");
    printf("%d-byte input: %.4f bytes per tick\n", SMALL_INPUT_SIZE, small_throughput);
    printf("%d-byte input: %.4f bytes per tick\n", MEDIUM_INPUT_SIZE, medium_throughput);
    printf("%d-byte input: %.4f bytes per tick\n", LARGE_INPUT_SIZE, large_throughput);
    
    /* Clean up */
    free(small_input);
    free(medium_input);
    free(large_input);
}

/* Test incremental hashing with multiple updates */
static void testIncrementalHashing()
{
    SHA256_CTX ctx;
    BYTE hash1[SHA256_BLOCK_SIZE], hash2[SHA256_BLOCK_SIZE];
    BYTE* data;
    int i, size = 1024;
    
    testCase("Incremental Hashing Test");
    
    /* Create test data */
    data = (BYTE*)malloc(size);
    generate_test_data(data, size, 54321);
    
    /* One-shot hashing */
    printf("Computing one-shot hash of %d bytes\n", size);
    sha256_init(&ctx);
    sha256_update(&ctx, data, size);
    sha256_final(&ctx, hash1);
    printf("One-shot hash: ");
    print_hash(hash1);
    
    /* Multi-update hashing (incremental) */
    printf("Computing incremental hash with multiple updates\n");
    sha256_init(&ctx);
    
    /* Update in chunks of different sizes */
    int position = 0;
    int chunk_sizes[] = {16, 64, 128, 256, 32, 528}; /* Sum should equal 'size' */
    int num_chunks = sizeof(chunk_sizes) / sizeof(chunk_sizes[0]);
    
    for (i = 0; i < num_chunks; i++) {
        printf("Update %d: %d bytes\n", i+1, chunk_sizes[i]);
        sha256_update(&ctx, data + position, chunk_sizes[i]);
        position += chunk_sizes[i];
    }
    
    sha256_final(&ctx, hash2);
    printf("Incremental hash: ");
    print_hash(hash2);
    
    /* Verify both methods produce the same hash */
    if (verify_hash(hash1, hash2)) {
        printf("Verification successful! Both methods produce identical hashes.\n");
    } else {
        printf("Verification failed! Hashes differ.\n");
    }
    
    assertTrue(verify_hash(hash1, hash2));
    free(data);
}

int main(void)
{
    msp430_setup();
    
    initTest();
    
    /* Run basic tests */
    testSimpleSHA256();
    testVariousInputSizes();
    
    /* Run more complex tests */
    testIncrementalHashing();
    
    /* Run benchmarks */
    benchmarkSHA256();
    
    printf("PROFILE\n"); 
    printf("EXIT\n");
    
    /* Short delay to allow serial output to finish */
    __delay_cycles(2000);
    
    return 0;
}