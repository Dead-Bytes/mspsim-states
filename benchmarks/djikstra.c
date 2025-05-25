/*
 * Dijkstra's Algorithm Implementation for MSP430
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
 
 /* Graph representation for the Dijkstra algorithm */
 #define MAX_NODES 60
 #define SMALL_NODES 25
 #define INF 9999
 
 typedef struct {
     int dist[MAX_NODES][MAX_NODES]; // Distance matrix
     int nodes;                      // Number of nodes
 } Graph;
 
 /* Initialize graph with default values */
 void initGraph(Graph* g, int nodes) {
     int i, j;
     g->nodes = nodes;
     
     /* Set all distances to INF initially */
     for (i = 0; i < nodes; i++) {
         for (j = 0; j < nodes; j++) {
             g->dist[i][j] = INF;
         }
         /* Distance to self is 0 */
         g->dist[i][i] = 0;
     }
 }
 
 /* Add an edge to the graph */
 void addEdge(Graph* g, int src, int dst, int weight) {
     g->dist[src][dst] = weight;
 }
 
 /* Dijkstra's algorithm implementation */
 void dijkstra(Graph* g, int src, int* distance) {
     int visited[MAX_NODES];
     int i, j, min_dist, min_index;
     
     /* Initialize distances from source and visited array */
     for (i = 0; i < g->nodes; i++) {
         distance[i] = g->dist[src][i];
         visited[i] = 0;
     }
     
     /* Mark source as visited */
     visited[src] = 1;
     
     /* Find shortest path for all nodes */
     for (i = 0; i < g->nodes - 1; i++) {
         /* Find the minimum distance vertex from the unvisited vertices */
         min_dist = INF;
         min_index = -1;
         
         for (j = 0; j < g->nodes; j++) {
             if (!visited[j] && distance[j] < min_dist) {
                 min_dist = distance[j];
                 min_index = j;
             }
         }
         
         /* If no minimum found, break (graph might be disconnected) */
         if (min_index == -1) {
             break;
         }
         
         /* Mark the selected vertex as visited */
         visited[min_index] = 1;
         
         /* Update distance values of adjacent vertices */
         for (j = 0; j < g->nodes; j++) {
             if (!visited[j] && 
                 g->dist[min_index][j] != INF && 
                 distance[min_index] + g->dist[min_index][j] < distance[j]) {
                 distance[j] = distance[min_index] + g->dist[min_index][j];
             }
         }
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
 
 /* Test a small graph */
 static void testSmallGraph() {
     Graph g;
     int distances[MAX_NODES];
     int i, j;
     
     testCase("Small Graph Dijkstra (100 nodes)");
     
     /* Create a graph with 100 nodes */
     initGraph(&g, SMALL_NODES);
     
     /* Add edges to the graph - simple pattern for test graph */
     for (i = 0; i < SMALL_NODES; i++) {
         /* Connect to next 5 nodes with increasing weights */
         for (j = 1; j <= 5; j++) {
             int dest = (i + j) % SMALL_NODES;
             addEdge(&g, i, dest, j * 2);
         }
         /* Add a few longer connections */
         addEdge(&g, i, (i + 10) % SMALL_NODES, 15);
         addEdge(&g, i, (i + 20) % SMALL_NODES, 18);
     }
     
     /* Run Dijkstra's algorithm with node 0 as source */
     dijkstra(&g, 0, distances);
     
     /* Print a sample of the distances */
     printf("Shortest distances from node 0 (samples):\n");
     for (i = 0; i < SMALL_NODES; i += 10) {
         printf("To node %d: %d\n", i, distances[i]);
     }
 }
 
 /* Test a larger graph for benchmarking */
 static void testLargeGraph() {
     Graph g;
     int distances[MAX_NODES];
     int i, j;
     unsigned int startTime, endTime;
     
     testCase("Large Graph Benchmark (500 nodes)");
     
     /* Create a graph with MAX_NODES nodes */
     initGraph(&g, MAX_NODES);
     
     /* Add edges - for large graph, connect each node to 10 others */
     for (i = 0; i < MAX_NODES; i++) {
         /* Connect to 10 nodes with pseudo-random weights */
         for (j = 1; j <= 10; j++) {
             int dest = (i + j * j) % MAX_NODES;
             if (i != dest) {
                 /* Weight between 1 and 20 */
                 addEdge(&g, i, dest, (i * 17 + dest * 13) % 20 + 1);
             }
         }
     }
     
     /* Measure execution time */
     dint();
     /* Select ACLK 32768Hz clock */
     TBCTL = TBSSEL0 | TBCLR | ID_0;
     TBCTL |= MC1; /* Start Timer_B in continuous mode */
     TBR = 0;
     
     startTime = TBR;
     
     /* Run Dijkstra once for the large graph */
     dijkstra(&g, 0, distances);
     
     endTime = TBR;
     
     /* Print execution time */
     printf("Execution time: %u timer ticks\n", endTime - startTime);
     
     /* Print a sample of the distances */
     printf("Shortest distances from node 0 (samples):\n");
     for (i = 0; i < MAX_NODES; i += 50) {
         printf("To node %d: %d\n", i, distances[i]);
     }
 }
 
 int main(void)
 {
     msp430_setup();
     
     initTest();
     
     /* Run the tests */
     testSmallGraph();
     
     /* Run benchmark */
     testLargeGraph();
     
     printf("PROFILE\n"); 
     printf("EXIT\n");
     
     /* Short delay to allow serial output to finish */
     __delay_cycles(2000);
     
     return 0;
 }