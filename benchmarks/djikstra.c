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
 #define MAX_NODES 10
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
     int i;
     
     testCase("Small Graph Dijkstra");
     
     /* Create a simple graph with 5 nodes */
     initGraph(&g, 5);
     
     /* Add edges to the graph */
     addEdge(&g, 0, 1, 4);
     addEdge(&g, 0, 2, 2);
     addEdge(&g, 1, 2, 5);
     addEdge(&g, 1, 3, 10);
     addEdge(&g, 2, 3, 3);
     addEdge(&g, 2, 4, 2);
     addEdge(&g, 3, 4, 7);
     
     /* Run Dijkstra's algorithm with node 0 as source */
     dijkstra(&g, 0, distances);
     
     /* Check if the shortest path calculations are correct */
     assertTrue(distances[0] == 0);  /* Distance to itself */
     assertTrue(distances[1] == 4);  /* 0->1 = 4 */
     assertTrue(distances[2] == 2);  /* 0->2 = 2 */
     assertTrue(distances[3] == 5);  /* 0->2->3 = 2+3 = 5 */
     assertTrue(distances[4] == 4);  /* 0->2->4 = 2+2 = 4 */
     
     /* Print the distances */
     printf("Shortest distances from node 0:\n");
     for (i = 0; i < g.nodes; i++) {
         printf("To node %d: %d\n", i, distances[i]);
     }
 }
 
 /* Test a larger graph for benchmarking */
 static void testLargeGraph() {
     Graph g;
     int distances[MAX_NODES];
     int i, j;
     unsigned int startTime, endTime;
     
     testCase("Large Graph Benchmark");
     
     /* Create a complete graph with MAX_NODES nodes */
     initGraph(&g, MAX_NODES);
     
     /* Add random weights to all edges */
     for (i = 0; i < MAX_NODES; i++) {
         for (j = 0; j < MAX_NODES; j++) {
             if (i != j) {
                 /* Random weight between 1 and 20 */
                 addEdge(&g, i, j, (i * 17 + j * 13) % 20 + 1);
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
     
     /* Run Dijkstra 10 times for better measurement */
     for (i = 0; i < 10; i++) {
         dijkstra(&g, i % MAX_NODES, distances);
     }
     
     endTime = TBR;
     
     /* Print execution time */
     printf("Execution time: %u timer ticks\n", endTime - startTime);
     
     /* Print the distances from the last run */
     printf("Shortest distances from node %d:\n", (9 % MAX_NODES));
     for (i = 0; i < g.nodes; i++) {
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