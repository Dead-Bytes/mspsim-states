/*
 * RSA Cryptography Implementation for MSP430
 * Based on the cputest.c benchmark structure
 * 
 * This is a simplified RSA implementation for benchmarking purposes
 * with small key sizes appropriate for resource-constrained devices.
 */

#include "msp430setup.h"
#include <stdio.h>
#include <string.h>

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

/* RSA parameters - using small keys appropriate for MSP430 */
#define SMALL_KEY_BITS 8    /* For tests - 8-bit keys */
#define MEDIUM_KEY_BITS 16  /* For benchmarks - 16-bit keys */
#define MAX_BUFFER 64       /* Maximum message size in chars */

/* Type definitions for RSA operations */
typedef unsigned long int RSA_int;

typedef struct {
    RSA_int n;      /* Modulus (n = p*q) */
    RSA_int e;      /* Public exponent */
} RSA_PublicKey;

typedef struct {
    RSA_int n;      /* Modulus (n = p*q) */
    RSA_int d;      /* Private exponent */
} RSA_PrivateKey;

typedef struct {
    RSA_PublicKey public_key;
    RSA_PrivateKey private_key;
} RSA_KeyPair;

/* Basic number theory functions needed for RSA */

/* Greatest Common Divisor using Euclidean algorithm */
RSA_int gcd(RSA_int a, RSA_int b) {
    RSA_int temp;
    while (b != 0) {
        temp = b;
        b = a % b;
        a = temp;
    }
    return a;
}

/* Extended Euclidean algorithm to find modular inverse */
RSA_int mod_inverse(RSA_int a, RSA_int m) {
    RSA_int m0 = m, t, q;
    RSA_int x0 = 0, x1 = 1;
    
    if (m == 1) {
        return 0;
    }
    
    while (a > 1) {
        /* q is quotient */
        q = a / m;
        
        /* m is remainder now */
        t = m;
        m = a % m;
        a = t;
        
        /* Update x0 and x1 */
        t = x0;
        x0 = x1 - q * x0;
        x1 = t;
    }
    
    /* Make x1 positive */
    if (x1 < 0) {
        x1 += m0;
    }
    
    return x1;
}

/* Modular exponentiation (a^b mod n) using square and multiply algorithm */
RSA_int mod_pow(RSA_int base, RSA_int exp, RSA_int mod) {
    RSA_int result = 1;
    base = base % mod;
    
    while (exp > 0) {
        /* If exp is odd, multiply result with base */
        if (exp & 1) {
            result = (result * base) % mod;
        }
        
        /* Square the base */
        base = (base * base) % mod;
        
        /* Divide exponent by 2 */
        exp >>= 1;
    }
    
    return result;
}

/* Generate a key pair for RSA with small primes for testing purposes */
void generate_key_pair(RSA_KeyPair* key_pair, RSA_int p, RSA_int q, RSA_int e) {
    RSA_int n = p * q;
    RSA_int phi = (p - 1) * (q - 1);
    RSA_int d = mod_inverse(e, phi);
    
    key_pair->public_key.n = n;
    key_pair->public_key.e = e;
    
    key_pair->private_key.n = n;
    key_pair->private_key.d = d;
}

/* RSA encryption operation */
RSA_int rsa_encrypt(RSA_int plaintext, RSA_PublicKey* key) {
    return mod_pow(plaintext, key->e, key->n);
}

/* RSA decryption operation */
RSA_int rsa_decrypt(RSA_int ciphertext, RSA_PrivateKey* key) {
    return mod_pow(ciphertext, key->d, key->n);
}

/* Encrypt a block of data */
void rsa_encrypt_block(RSA_int* input, RSA_int* output, int size, RSA_PublicKey* key) {
    int i;
    for (i = 0; i < size; i++) {
        output[i] = rsa_encrypt(input[i], key);
    }
}

/* Decrypt a block of data */
void rsa_decrypt_block(RSA_int* input, RSA_int* output, int size, RSA_PrivateKey* key) {
    int i;
    for (i = 0; i < size; i++) {
        output[i] = rsa_decrypt(input[i], key);
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

/* Test small RSA encryption and decryption */
static void testSmallRSA() {
    RSA_KeyPair key_pair;
    RSA_int plaintext[MAX_BUFFER];
    RSA_int ciphertext[MAX_BUFFER];
    RSA_int decrypted[MAX_BUFFER];
    int i, success;
    
    testCase("Small RSA Test (8-bit keys)");
    
    /* Generate key pair with small primes for testing */
    /* Using p=11, q=13, e=7 */
    generate_key_pair(&key_pair, 11, 13, 7);
    
    printf("Key generation complete:\n");
    printf("Public key (n=%lu, e=%lu)\n", key_pair.public_key.n, key_pair.public_key.e);
    printf("Private key (n=%lu, d=%lu)\n", key_pair.private_key.n, key_pair.private_key.d);
    
    /* Test with small messages */
    printf("Testing encryption and decryption with small values...\n");
    
    for (i = 0; i < 10; i++) {
        plaintext[i] = i + 10; /* Small numbers that fit in our key size */
        ciphertext[i] = rsa_encrypt(plaintext[i], &key_pair.public_key);
        decrypted[i] = rsa_decrypt(ciphertext[i], &key_pair.private_key);
        
        printf("Value %2lu: encrypted=%3lu, decrypted=%2lu ", 
               plaintext[i], ciphertext[i], decrypted[i]);
        
        if (plaintext[i] == decrypted[i]) {
            printf("✓\n");
        } else {
            printf("✗\n");
        }
    }
    
    /* Verify all decryptions were successful */
    success = 1;
    for (i = 0; i < 10; i++) {
        if (plaintext[i] != decrypted[i]) {
            success = 0;
            break;
        }
    }
    
    assertTrue(success);
}

/* Benchmark RSA operations */
static void benchmarkMediumRSA() {
    RSA_KeyPair key_pair;
    RSA_int plaintext[MAX_BUFFER];
    RSA_int ciphertext[MAX_BUFFER];
    RSA_int decrypted[MAX_BUFFER];
    int i, success_count;
    unsigned int startTime, endTime, encryptTime, decryptTime;
    
    testCase("Medium RSA Benchmark (16-bit keys)");
    
    /* Generate key pair with 16-bit modulus */
    /* Using p=173, q=149, e=3 */
    generate_key_pair(&key_pair, 173, 149, 3);
    
    printf("Key generation complete:\n");
    printf("Public key (n=%lu, e=%lu)\n", key_pair.public_key.n, key_pair.public_key.e);
    printf("Private key (n=%lu, d=%lu)\n", key_pair.private_key.n, key_pair.private_key.d);
    
    /* Generate test data */
    for (i = 0; i < MAX_BUFFER; i++) {
        /* Ensure values are within range of modulus */
        plaintext[i] = (i * 123) % key_pair.public_key.n;
        if (plaintext[i] == 0) plaintext[i] = 1; /* Avoid zero */
    }
    
    /* Setup timer */
    dint();
    /* Select ACLK 32768Hz clock */
    TBCTL = TBSSEL0 | TBCLR | ID_0;
    TBCTL |= MC1; /* Start Timer_B in continuous mode */
    TBR = 0;
    
    /* Benchmark encryption */
    startTime = TBR;
    
    /* Encrypt test data */
    rsa_encrypt_block(plaintext, ciphertext, MAX_BUFFER, &key_pair.public_key);
    
    endTime = TBR;
    encryptTime = endTime - startTime;
    
    /* Benchmark decryption */
    startTime = TBR;
    
    /* Decrypt test data */
    rsa_decrypt_block(ciphertext, decrypted, MAX_BUFFER, &key_pair.private_key);
    
    endTime = TBR;
    decryptTime = endTime - startTime;
    
    /* Print benchmark results */
    printf("RSA Encryption time for %d blocks: %u timer ticks\n", 
           MAX_BUFFER, encryptTime);
    printf("RSA Decryption time for %d blocks: %u timer ticks\n", 
           MAX_BUFFER, decryptTime);
    printf("Total time: %u timer ticks\n", encryptTime + decryptTime);
    
    /* Verify results */
    success_count = 0;
    for (i = 0; i < MAX_BUFFER; i++) {
        if (plaintext[i] == decrypted[i]) {
            success_count++;
        }
    }
    
    printf("Successfully decrypted %d/%d blocks\n", success_count, MAX_BUFFER);
    assertTrue(success_count == MAX_BUFFER);
}

/* Test performance with varying key sizes */
static void testDifferentKeySizes() {
    RSA_KeyPair key_pair;
    RSA_int plaintext, ciphertext, decrypted;
    unsigned int startTime, endTime;
    
    testCase("Different Key Size Tests");
    
    /* Small key: p=11, q=13, e=7 */
    generate_key_pair(&key_pair, 11, 13, 7);
    plaintext = 15;
    
    startTime = TBR;
    ciphertext = rsa_encrypt(plaintext, &key_pair.public_key);
    decrypted = rsa_decrypt(ciphertext, &key_pair.private_key);
    endTime = TBR;
    
    printf("8-bit key (n=%lu) time: %u timer ticks\n", 
           key_pair.public_key.n, endTime - startTime);
    assertTrue(plaintext == decrypted);
    
    /* Medium key: p=173, q=149, e=3 */
    generate_key_pair(&key_pair, 173, 149, 3);
    plaintext = 1234;
    
    startTime = TBR;
    ciphertext = rsa_encrypt(plaintext, &key_pair.public_key);
    decrypted = rsa_decrypt(ciphertext, &key_pair.private_key);
    endTime = TBR;
    
    printf("16-bit key (n=%lu) time: %u timer ticks\n", 
           key_pair.public_key.n, endTime - startTime);
    assertTrue(plaintext == decrypted);
}

int main(void)
{
    msp430_setup();
    
    initTest();
    
    /* Run the basic tests */
    testSmallRSA();
    
    /* Run benchmarks */
    benchmarkMediumRSA();
    
    /* Test different key sizes */
    testDifferentKeySizes();
    
    printf("PROFILE\n"); 
    printf("EXIT\n");
    
    /* Short delay to allow serial output to finish */
    __delay_cycles(2000);
    
    return 0;
}