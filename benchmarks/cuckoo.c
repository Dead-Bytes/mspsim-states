/*
 * Cuckoo Hashing Implementation for MSP430
 * Based on the cputest.c benchmark structure
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

/* Cuckoo Hashing parameters */
#define TABLE_SIZE 256      /* Size of each hash table */
#define NUM_TABLES 2        /* Number of hash tables (2 for basic cuckoo hashing) */
#define MAX_LOOP 100        /* Maximum number of relocations before rehashing */
#define SMALL_SET_SIZE 100  /* Size of small test set */
#define LARGE_SET_SIZE 1000  /* Size of large benchmark set */

/* Cuckoo hash table structure */
typedef struct {
    int tables[NUM_TABLES][TABLE_SIZE];
    int positions[NUM_TABLES][TABLE_SIZE]; /* To store actual keys */
    int size;                             /* Current number of elements */
} CuckooHash;

/* Initialize hash table */
void initCuckooHash(CuckooHash* ch) {
    int i, j;
    ch->size = 0;
    
    /* Mark all slots as empty with -1 */
    for (i = 0; i < NUM_TABLES; i++) {
        for (j = 0; j < TABLE_SIZE; j++) {
            ch->tables[i][j] = -1;
            ch->positions[i][j] = -1;
        }
    }
}

/* Hash functions for the two tables
 * Using different multiplication factors for each table */
int hash1(int key) {
    return (key * 2654435761U) % TABLE_SIZE;
}

int hash2(int key) {
    return (key * 1046527359U) % TABLE_SIZE;
}

/* Get hash slot for given table index */
int getHash(int key, int tableIdx) {
    if (tableIdx == 0) {
        return hash1(key);
    } else {
        return hash2(key);
    }
}

/* Insert key-value pair into the cuckoo hash */
int insertItem(CuckooHash* ch, int key, int value) {
    int currTable, currKey, currValue, pos, i;
    int loop;
    
    currKey = key;
    currValue = value;
    currTable = 0;
    
    /* Try to insert with max iterations to avoid infinite loops */
    for (loop = 0; loop < MAX_LOOP; loop++) {
        pos = getHash(currKey, currTable);
        
        /* If empty slot found, insert and finish */
        if (ch->tables[currTable][pos] == -1) {
            ch->tables[currTable][pos] = currValue;
            ch->positions[currTable][pos] = currKey;
            ch->size++;
            return 1; /* Success */
        }
        
        /* Otherwise, kick out existing item and insert current one */
        int oldKey = ch->positions[currTable][pos];
        int oldValue = ch->tables[currTable][pos];
        
        ch->tables[currTable][pos] = currValue;
        ch->positions[currTable][pos] = currKey;
        
        /* Update current key/value to be the one we kicked out */
        currKey = oldKey;
        currValue = oldValue;
        currTable = (currTable + 1) % NUM_TABLES;
    }
    
    /* If we get here, we've hit the maximum number of relocations 
     * In a real implementation we would rehash with larger tables,
     * but for benchmarking we just report failure */
    return 0; /* Failure - cycle detected */
}

/* Search for a key and return its value */
int search(CuckooHash* ch, int key) {
    int i, pos;
    
    for (i = 0; i < NUM_TABLES; i++) {
        pos = getHash(key, i);
        if (ch->positions[i][pos] == key) {
            return ch->tables[i][pos];
        }
    }
    
    return -1; /* Not found */
}

/* Delete a key from the hash table */
int delete(CuckooHash* ch, int key) {
    int i, pos;
    
    for (i = 0; i < NUM_TABLES; i++) {
        pos = getHash(key, i);
        if (ch->positions[i][pos] == key) {
            ch->tables[i][pos] = -1;
            ch->positions[i][pos] = -1;
            ch->size--;
            return 1; /* Success */
        }
    }
    
    return 0; /* Not found */
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

/* Test small hash table operations */
static void testSmallCuckoo() {
    CuckooHash ch;
    int i, value;
    int success_count = 0;
    
    testCase("Small Cuckoo Hash Test (100 entries)");
    
    /* Initialize hash table */
    initCuckooHash(&ch);
    
    /* Insert items */
    printf("Inserting %d items...\n", SMALL_SET_SIZE);
    for (i = 0; i < SMALL_SET_SIZE; i++) {
        if (insertItem(&ch, i, i * 10)) {
            success_count++;
        }
    }
    printf("Successfully inserted %d items\n", success_count);
    
    /* Search for items */
    success_count = 0;
    for (i = 0; i < SMALL_SET_SIZE; i++) {
        value = search(&ch, i);
        if (value == i * 10) {
            success_count++;
        }
    }
    printf("Successfully found %d/%d items\n", success_count, SMALL_SET_SIZE);
    
    /* Delete some items */
    success_count = 0;
    for (i = 0; i < SMALL_SET_SIZE; i += 2) {
        if (delete(&ch, i)) {
            success_count++;
        }
    }
    printf("Successfully deleted %d items\n", success_count);
    
    /* Verify deletions worked */
    int found_deleted = 0;
    int found_remaining = 0;
    for (i = 0; i < SMALL_SET_SIZE; i++) {
        value = search(&ch, i);
        if (i % 2 == 0) {
            /* Should be deleted */
            if (value == -1) {
                found_deleted++;
            }
        } else {
            /* Should still exist */
            if (value == i * 10) {
                found_remaining++;
            }
        }
    }
    printf("Correctly deleted: %d/%d\n", found_deleted, SMALL_SET_SIZE / 2);
    printf("Correctly remaining: %d/%d\n", found_remaining, SMALL_SET_SIZE / 2);
}

/* Benchmark large operations */
static void benchmarkLargeCuckoo() {
    CuckooHash ch;
    int i, value;
    int success_count = 0;
    unsigned int startTime, endTime, insertTime, searchTime, deleteTime;
    
    testCase("Large Cuckoo Hash Benchmark (500 entries)");
    
    /* Initialize hash table */
    initCuckooHash(&ch);
    
    /* Measure insert operation time */
    dint();
    /* Select ACLK 32768Hz clock */
    TBCTL = TBSSEL0 | TBCLR | ID_0;
    TBCTL |= MC1; /* Start Timer_B in continuous mode */
    TBR = 0;
    
    startTime = TBR;
    
    /* Insert benchmark */
    for (i = 0; i < LARGE_SET_SIZE; i++) {
        if (insertItem(&ch, i, i * 10)) {
            success_count++;
        }
    }
    
    endTime = TBR;
    insertTime = endTime - startTime;
    
    /* Search benchmark */
    success_count = 0;
    startTime = TBR;
    
    for (i = 0; i < LARGE_SET_SIZE; i++) {
        value = search(&ch, i);
        if (value == i * 10) {
            success_count++;
        }
    }
    
    endTime = TBR;
    searchTime = endTime - startTime;
    
    /* Delete benchmark - delete half the entries */
    success_count = 0;
    startTime = TBR;
    
    for (i = 0; i < LARGE_SET_SIZE; i += 2) {
        if (delete(&ch, i)) {
            success_count++;
        }
    }
    
    endTime = TBR;
    deleteTime = endTime - startTime;
    
    /* Print benchmark results */
    printf("Insertion time for %d items: %u timer ticks\n", LARGE_SET_SIZE, insertTime);
    printf("Search time for %d items: %u timer ticks\n", LARGE_SET_SIZE, searchTime);
    printf("Deletion time for %d items: %u timer ticks\n", LARGE_SET_SIZE/2, deleteTime);
    printf("Total time: %u timer ticks\n", insertTime + searchTime + deleteTime);
    
    /* Verify hash table is functioning correctly */
    int found_deleted = 0;
    int found_remaining = 0;
    for (i = 0; i < LARGE_SET_SIZE; i++) {
        value = search(&ch, i);
        if (i % 2 == 0) {
            /* Should be deleted */
            if (value == -1) {
                found_deleted++;
            }
        } else {
            /* Should still exist */
            if (value == i * 10) {
                found_remaining++;
            }
        }
    }
    
    printf("Hash verification after benchmark:\n");
    printf("Correctly deleted: %d/%d\n", found_deleted, LARGE_SET_SIZE / 2);
    printf("Correctly remaining: %d/%d\n", found_remaining, LARGE_SET_SIZE / 2);
    printf("Load factor: %.2f\n", (float)ch.size / (NUM_TABLES * TABLE_SIZE));
}

int main(void)
{
    msp430_setup();
    
    initTest();
    
    /* Run the tests */
    testSmallCuckoo();
    
    /* Run benchmark */
    benchmarkLargeCuckoo();
    
    printf("PROFILE\n"); 
    printf("EXIT\n");
    
    /* Short delay to allow serial output to finish */
    __delay_cycles(2000);
    
    return 0;
}