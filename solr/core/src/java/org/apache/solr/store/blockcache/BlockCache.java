package org.apache.solr.store.blockcache;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.googlecode.concurrentlinkedhashmap.EvictionListener;

public class BlockCache {

  public static final int _128M = 134217728;
  public static final int _32K = 32768;
  private final ConcurrentMap<BlockCacheKey, BlockCacheLocation> _cache;
  private final ByteBuffer[] _banks;
  private final BlockLocks[] _locks;
  private final AtomicInteger[] _lockCounters;
  private final int _blockSize;
  private final int _numberOfBlocksPerBank;
  private final int _maxEntries;
  private final Metrics _metrics;
  
  public BlockCache(Metrics metrics, boolean directAllocation, long totalMemory) {
    this(metrics, directAllocation,totalMemory,_128M);
  }
  
  public BlockCache(Metrics metrics, boolean directAllocation, long totalMemory, int slabSize) {
    this(metrics, directAllocation,totalMemory,slabSize,_32K);
  }
  
  public BlockCache(Metrics metrics, boolean directAllocation, long totalMemory, int slabSize, int blockSize) {
  _metrics = metrics;
    _numberOfBlocksPerBank = slabSize / blockSize;
    int numberOfBanks = (int) (totalMemory / slabSize);
    
    _banks = new ByteBuffer[numberOfBanks];
    _locks = new BlockLocks[numberOfBanks];
    _lockCounters = new AtomicInteger[numberOfBanks];
    _maxEntries = (_numberOfBlocksPerBank * numberOfBanks) - 1;
    for (int i = 0; i < numberOfBanks; i++) {
      if (directAllocation) {
        _banks[i] = ByteBuffer.allocateDirect(_numberOfBlocksPerBank * blockSize);
      } else {
        _banks[i] = ByteBuffer.allocate(_numberOfBlocksPerBank * blockSize);
      }
      _locks[i] = new BlockLocks(_numberOfBlocksPerBank);
      _lockCounters[i] = new AtomicInteger();
    }

    EvictionListener<BlockCacheKey, BlockCacheLocation> listener = new EvictionListener<BlockCacheKey, BlockCacheLocation>() {
      @Override
      public void onEviction(BlockCacheKey key, BlockCacheLocation location) {
        releaseLocation(location);
      }
    };
    _cache = new ConcurrentLinkedHashMap.Builder<BlockCacheKey, BlockCacheLocation>().maximumWeightedCapacity(_maxEntries).listener(listener).build();
    _blockSize = blockSize;
  }

  private void releaseLocation(BlockCacheLocation location) {
    if (location == null) {
      return;
    }
    int bankId = location.getBankId();
    int block = location.getBlock();
    location.setRemoved(true);
    _locks[bankId].clear(block);
    _lockCounters[bankId].decrementAndGet();
    _metrics.blockCacheEviction.incrementAndGet();
    _metrics.blockCacheSize.decrementAndGet();
  }

  public boolean store(BlockCacheKey blockCacheKey, int blockOffset, byte[] data, int offset, int length) {
    if (length + blockOffset > _blockSize) {
      throw new RuntimeException("Buffer size exceeded, expecting max ["
          + _blockSize + "] got length [" + length + "] with blockOffset [" + blockOffset + "]" );
    }
    BlockCacheLocation location = _cache.get(blockCacheKey);
    boolean newLocation = false;
    if (location == null) {
      newLocation = true;
      location = new BlockCacheLocation();
      if (!findEmptyLocation(location)) {
        return false;
      }
    }
    if (location.isRemoved()) {
      return false;
    }
    int bankId = location.getBankId();
    int bankOffset = location.getBlock() * _blockSize;
    ByteBuffer bank = getBank(bankId);
    bank.position(bankOffset + blockOffset);
    bank.put(data, offset, length);
    if (newLocation) {
      releaseLocation(_cache.put(blockCacheKey.clone(), location));
      _metrics.blockCacheSize.incrementAndGet();
    }
    return true;
  }

  public boolean fetch(BlockCacheKey blockCacheKey, byte[] buffer, int blockOffset, int off, int length) {
    BlockCacheLocation location = _cache.get(blockCacheKey);
    if (location == null) {
      return false;
    }
    if (location.isRemoved()) {
      return false;
    }
    int bankId = location.getBankId();
    int offset = location.getBlock() * _blockSize;
    location.touch();
    ByteBuffer bank = getBank(bankId);
    bank.position(offset + blockOffset);
    bank.get(buffer, off, length);
    return true;
  }

  public boolean fetch(BlockCacheKey blockCacheKey, byte[] buffer) {
    checkLength(buffer);
    return fetch(blockCacheKey, buffer, 0, 0, _blockSize);
  }

  private boolean findEmptyLocation(BlockCacheLocation location) {
    // This is a tight loop that will try and find a location to
    // place the block before giving up
    for (int j = 0; j < 10; j++) {
      OUTER: for (int bankId = 0; bankId < _banks.length; bankId++) {
        AtomicInteger bitSetCounter = _lockCounters[bankId];
        BlockLocks bitSet = _locks[bankId];
        if (bitSetCounter.get() == _numberOfBlocksPerBank) {
          // if bitset is full
          continue OUTER;
        }
        // this check needs to spin, if a lock was attempted but not obtained
        // the rest of the bank should not be skipped
        int bit = bitSet.nextClearBit(0);
        INNER: while (bit != -1) {
          if (bit >= _numberOfBlocksPerBank) {
            // bit set is full
            continue OUTER;
          }
          if (!bitSet.set(bit)) {
            // lock was not obtained
            // this restarts at 0 because another block could have been unlocked
            // while this was executing
            bit = bitSet.nextClearBit(0);
            continue INNER;
          } else {
            // lock obtained
            location.setBankId(bankId);
            location.setBlock(bit);
            bitSetCounter.incrementAndGet();
            return true;
          }
        }
      }
    }
    return false;
  }

  private void checkLength(byte[] buffer) {
    if (buffer.length != _blockSize) {
      throw new RuntimeException("Buffer wrong size, expecting [" + _blockSize + "] got [" + buffer.length + "]");
    }
  }

  private ByteBuffer getBank(int bankId) {
    return _banks[bankId].duplicate();
  }

  public int getSize() {
    return _cache.size();
  }
}
