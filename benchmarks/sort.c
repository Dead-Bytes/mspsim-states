/*
 * Sorting Algorithms Implementation for MSP430
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

/* Sorting benchmark parameters */
#define SMALL_ARRAY_SIZE 100
#define LARGE_ARRAY_SIZE 500
#define MAX_ARRAY_SIZE 1000

/* Function to generate pseudo-random data */
void generateData(int* array, int size, int seed) {
    int i;
    int next = seed;
    
    for (i = 0; i < size; i++) {
        /* Simple linear congruential generator */
        next = (next * 1103515245 + 12345) & 0x7FFFFFFF;
        array[i] = next % 10000; /* Values 0-9999 */
    }
}

/* Verify array is sorted */
int isSorted(int* array, int size) {
    int i;
    for (i = 1; i < size; i++) {
        if (array[i] < array[i-1]) {
            return 0; /* Not sorted */
        }
    }
    return 1; /* Sorted */
}

/* Copy array contents */
void copyArray(int* dest, int* src, int size) {
    int i;
    for (i = 0; i < size; i++) {
        dest[i] = src[i];
    }
}

/* Insertion Sort implementation */
void insertionSort(int* array, int size) {
    int i, j, key;
    
    for (i = 1; i < size; i++) {
        key = array[i];
        j = i - 1;
        
        /* Move elements of array[0..i-1], that are greater than key, 
           one position ahead of their current position */
        while (j >= 0 && array[j] > key) {
            array[j + 1] = array[j];
            j--;
        }
        array[j + 1] = key;
    }
}

/* Bubble Sort implementation */
void bubbleSort(int* array, int size) {
    int i, j;
    int swapped;
    
    for (i = 0; i < size - 1; i++) {
        swapped = 0;
        
        for (j = 0; j < size - i - 1; j++) {
            if (array[j] > array[j + 1]) {
                /* Swap */
                int temp = array[j];
                array[j] = array[j + 1];
                array[j + 1] = temp;
                swapped = 1;
            }
        }
        
        /* If no swapping occurred in this pass, array is sorted */
        if (!swapped) {
            break;
        }
    }
}

/* Helper function for quickSort */
int partition(int* array, int low, int high) {
    int pivot = array[high]; /* Select last element as pivot */
    int i = low - 1;
    int j;
    
    for (j = low; j <= high - 1; j++) {
        /* If current element is smaller than the pivot */
        if (array[j] < pivot) {
            i++; /* Increment index of smaller element */
            
            /* Swap array[i] and array[j] */
            int temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
    }
    
    /* Swap array[i + 1] and array[high] (put pivot in correct position) */
    int temp = array[i + 1];
    array[i + 1] = array[high];
    array[high] = temp;
    
    return i + 1; /* Return partition position */
}

/* QuickSort implementation */
void quickSortRecursive(int* array, int low, int high) {
    if (low < high) {
        /* Find partition index */
        int pi = partition(array, low, high);
        
        /* Sort elements before and after partition */
        quickSortRecursive(array, low, pi - 1);
        quickSortRecursive(array, pi + 1, high);
    }
}

/* QuickSort wrapper function */
void quickSort(int* array, int size) {
    quickSortRecursive(array, 0, size - 1);
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

/* Test small array sorting */
static void testSmallSort() {
    int originalArray[SMALL_ARRAY_SIZE];
    int testArray[SMALL_ARRAY_SIZE];
    int i;
    
    testCase("Small Array Sort Test (100 elements)");
    
    /* Generate test data */
    generateData(originalArray, SMALL_ARRAY_SIZE, 12345);
    
    /* Test Insertion Sort */
    copyArray(testArray, originalArray, SMALL_ARRAY_SIZE);
    printf("Testing Insertion Sort... ");
    insertionSort(testArray, SMALL_ARRAY_SIZE);
    if (isSorted(testArray, SMALL_ARRAY_SIZE)) {
        printf("Correctly sorted!\n");
    } else {
        printf("Failed to sort properly!\n");
    }
    
    /* Test Bubble Sort */
    copyArray(testArray, originalArray, SMALL_ARRAY_SIZE);
    printf("Testing Bubble Sort... ");
    bubbleSort(testArray, SMALL_ARRAY_SIZE);
    if (isSorted(testArray, SMALL_ARRAY_SIZE)) {
        printf("Correctly sorted!\n");
    } else {
        printf("Failed to sort properly!\n");
    }
    
    /* Test Quick Sort */
    copyArray(testArray, originalArray, SMALL_ARRAY_SIZE);
    printf("Testing Quick Sort... ");
    quickSort(testArray, SMALL_ARRAY_SIZE);
    if (isSorted(testArray, SMALL_ARRAY_SIZE)) {
        printf("Correctly sorted!\n");
    } else {
        printf("Failed to sort properly!\n");
    }
    
    /* Print small sample of sorted array */
    printf("Sample of sorted array (first 10 elements): ");
    for (i = 0; i < 10; i++) {
        printf("%d ", testArray[i]);
    }
    printf("\n");
}

/* Benchmark large array sorting */
static void benchmarkLargeSort() {
    int originalArray[LARGE_ARRAY_SIZE];
    int testArray[LARGE_ARRAY_SIZE];
    unsigned int startTime, endTime;
    int i;
    
    testCase("Large Array Sort Benchmark (500 elements)");
    
    /* Generate test data - using a smaller seed to avoid overflow */
    generateData(originalArray, LARGE_ARRAY_SIZE, 12345);
    
    /* Benchmark Insertion Sort */
    copyArray(testArray, originalArray, LARGE_ARRAY_SIZE);
    
    /* Setup timer */
    dint();
    /* Select ACLK 32768Hz clock */
    TBCTL = TBSSEL0 | TBCLR | ID_0;
    TBCTL |= MC1; /* Start Timer_B in continuous mode */
    TBR = 0;
    
    startTime = TBR;
    insertionSort(testArray, LARGE_ARRAY_SIZE);
    endTime = TBR;
    
    printf("Insertion Sort time for %d elements: %u timer ticks\n", 
           LARGE_ARRAY_SIZE, endTime - startTime);
    assertTrue(isSorted(testArray, LARGE_ARRAY_SIZE));
    
    /* Benchmark Bubble Sort */
    copyArray(testArray, originalArray, LARGE_ARRAY_SIZE);
    
    startTime = TBR;
    bubbleSort(testArray, LARGE_ARRAY_SIZE);
    endTime = TBR;
    
    printf("Bubble Sort time for %d elements: %u timer ticks\n", 
           LARGE_ARRAY_SIZE, endTime - startTime);
    assertTrue(isSorted(testArray, LARGE_ARRAY_SIZE));
    
    /* Benchmark Quick Sort */
    copyArray(testArray, originalArray, LARGE_ARRAY_SIZE);
    
    startTime = TBR;
    quickSort(testArray, LARGE_ARRAY_SIZE);
    endTime = TBR;
    
    printf("Quick Sort time for %d elements: %u timer ticks\n", 
           LARGE_ARRAY_SIZE, endTime - startTime);
    assertTrue(isSorted(testArray, LARGE_ARRAY_SIZE));
    
    /* Print small sample of sorted array */
    printf("Sample of sorted array (first 10 elements): ");
    for (i = 0; i < 10; i++) {
        printf("%d ", testArray[i]);
    }
    printf("\n");
}

/* Test different input characteristics */
static void testInputCharacteristics() {
    int testArray[SMALL_ARRAY_SIZE];
    int i;
    unsigned int startTime, endTime;
    
    testCase("Input Characteristics Test");
    
    /* Test already sorted data with Quick Sort */
    for (i = 0; i < SMALL_ARRAY_SIZE; i++) {
        testArray[i] = i;
    }
    
    startTime = TBR;
    quickSort(testArray, SMALL_ARRAY_SIZE);
    endTime = TBR;
    
    printf("Quick Sort - already sorted: %u timer ticks\n", endTime - startTime);
    
    /* Test reverse sorted data with Quick Sort */
    for (i = 0; i < SMALL_ARRAY_SIZE; i++) {
        testArray[i] = SMALL_ARRAY_SIZE - i;
    }
    
    startTime = TBR;
    quickSort(testArray, SMALL_ARRAY_SIZE);
    endTime = TBR;
    
    printf("Quick Sort - reverse sorted: %u timer ticks\n", endTime - startTime);
    
    /* Test data with many duplicates */
    for (i = 0; i < SMALL_ARRAY_SIZE; i++) {
        testArray[i] = i % 10;  /* Only 10 unique values */
    }
    
    startTime = TBR;
    quickSort(testArray, SMALL_ARRAY_SIZE);
    endTime = TBR;
    
    printf("Quick Sort - many duplicates: %u timer ticks\n", endTime - startTime);
}

int main(void)
{
    msp430_setup();
    
    initTest();
    
    /* Run the basic tests */
    testSmallSort();
    
    /* Run benchmarks */
    benchmarkLargeSort();
    
    /* Test different input characteristics */
    testInputCharacteristics();
    
    printf("PROFILE\n"); 
    printf("EXIT\n");
    
    /* Short delay to allow serial output to finish */
    __delay_cycles(2000);
    
    return 0;
}