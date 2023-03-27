package org.bonitasoft.reporting.services;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;

class MedianOfLongStream {

    private Queue<Long> minHeap, maxHeap;

    MedianOfLongStream() {
        minHeap = new PriorityQueue<>();
        maxHeap = new PriorityQueue<>(Comparator.reverseOrder());
    }

    void add(long num) {
        if (minHeap.size() == maxHeap.size()) {
            maxHeap.offer(num);
            minHeap.offer(maxHeap.poll());
        } else {
            minHeap.offer(num);
            maxHeap.offer(minHeap.poll());
        }
    }

    double getMedian() {
        long median;
        if (minHeap.size() > maxHeap.size()) {
            median = minHeap.peek();
        } else if (!minHeap.isEmpty()) {
            median = (minHeap.peek() + maxHeap.peek()) / 2;
        } else {
            median = 0;
        }
        return median;
    }
}