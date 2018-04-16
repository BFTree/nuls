/*
 * MIT License
 *
 * Copyright (c) 2017-2018 nuls.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.nuls.protocol.base.entity.block;

import io.nuls.protocol.base.cache.manager.block.BlockCacheBuffer;
import io.nuls.protocol.base.cache.manager.block.ConfirmingBlockCacheManager;
import io.nuls.protocol.base.constant.PocConsensusConstant;
import io.nuls.protocol.intf.BlockService;
import io.nuls.core.chain.entity.BlockHeader;
import io.nuls.core.context.NulsContext;
import io.nuls.core.exception.NulsException;
import io.nuls.core.utils.log.BlockLog;
import io.nuls.core.utils.log.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Niels
 * @date 2018/1/12
 */
public class BifurcateProcessor {

    private static final BifurcateProcessor INSTANCE = new BifurcateProcessor();

    private ConfirmingBlockCacheManager confirmingBlockCacheManager = ConfirmingBlockCacheManager.getInstance();

    private BlockCacheBuffer blockCacheBuffer = BlockCacheBuffer.getInstance();

    private BlockHeaderChain approvingChain;

    private List<BlockHeaderChain> chainList = new CopyOnWriteArrayList<>();
    private long maxHeight;

    private BifurcateProcessor() {
    }

    public static BifurcateProcessor getInstance() {
        return INSTANCE;
    }

    public boolean addHeader(BlockHeader header) {
        boolean needUpdateBestBlock = false;
        boolean result = add(header);
        if (result) {
            if (header.getHeight() > maxHeight) {
                maxHeight = header.getHeight();
                needUpdateBestBlock = true;
            }
            checkIt();
        }
        return needUpdateBestBlock;
    }

    private void checkIt() {
        long maxHeight = 0L;
        BlockHeaderChain longestChain = null;
        StringBuilder str = new StringBuilder("++++++++++++++++++++++++chain info:");
        for (BlockHeaderChain chain : chainList) {
            if (chain.size() == 0) {
                continue;
            }
            String type = "";
            if (approvingChain != null && chain.getId().equals(approvingChain.getId())) {
                type = " approving ";
            }
            str.append("\nid:"+chain.getId()+"," + type + "chain:start-" + chain.getHeaderDigestList().get(0).getHeight() + ", end-" + chain.getLastHd().getHeight());
            long height = chain.getLastHd().getHeight();
            if (maxHeight < height) {
                maxHeight = height;
                longestChain = chain;
            } else if (maxHeight == height) {
                HeaderDigest hd = chain.getLastHd();
                HeaderDigest hd_long = longestChain.getLastHd();
                if (hd.getTime() < hd_long.getTime()) {
                    longestChain = chain;
                }
            }
        }
        if (null == longestChain) {
            BlockLog.debug("the longest chain not found!");
            return;
        }
        BlockLog.debug(str.toString()+"\n the longest is:"+longestChain.getId());
        BlockHeaderChain lastApprovingChain  = this.approvingChain;
        this.approvingChain = longestChain;
        if (lastApprovingChain != null && !lastApprovingChain.getId().equals(longestChain.getId())) {
            BlockService blockService = NulsContext.getServiceBean(BlockService.class);
            List<HeaderDigest> nextChain = new ArrayList<>(longestChain.getHeaderDigestList());
            List<HeaderDigest> lastChain = new ArrayList<>(lastApprovingChain.getHeaderDigestList());
            List<HeaderDigest> rollbackChain = new ArrayList<>();

            for(int i = lastChain.size()-1;i>=0;i--){
                HeaderDigest hd = lastChain.get(i);
                if(nextChain.contains(hd)){
                    break;
                }
                rollbackChain.add(hd);
            }
            for(int i = rollbackChain.size()-1;i>=0;i--){
                try {
                    blockService.rollbackBlock(rollbackChain.get(i).getHash());
                } catch (NulsException e) {
                    Log.error(e);
                }
            }
//            List<HeaderDigest> hdList = new ArrayList<>(approvingChain.getHeaderDigestList());
//            for (int i = hdList.size() - 1; i >= 0; i--) {
//                HeaderDigest hd = hdList.get(i);
//                if (longestChain.contains(hd)) {
//                    break;
//                }
//                try {
//                    blockService.rollbackBlock(hd.getHash());
//                } catch (NulsException e) {
//                    Log.error(e);
//                }
//            }
//            List<HeaderDigest> longestHdList = new ArrayList<>(longestChain.getHeaderDigestList());
//            for (int i = 0; i < longestHdList.size(); i++) {
//                HeaderDigest hd = longestHdList.get(i);
//                if (approvingChain.contains(hd)) {
//                    continue;
//                }
//                blockService.approvalBlock(hd.getHash());
//            }
        }

    }

    private boolean add(BlockHeader header) {
        for (int i = 0; i < this.chainList.size(); i++) {
            BlockHeaderChain chain = chainList.get(i);
            if (chain.contains(header)) {
                return false;
            }
        }

        for (int i = 0; i < this.chainList.size(); i++) {
            BlockHeaderChain chain = chainList.get(i);
            HeaderDigest lastHeader = chain.getLastHd();
            if (null != lastHeader && header.getPreHash().getDigestHex().equals(lastHeader.getHash())) {
                chain.addHeader(header);
                return true;
            } else if (chain.contains(new HeaderDigest(header.getPreHash().getDigestHex(), header.getHeight() - 1, 0L))) {
                BlockHeaderChain newChain = chain.getBifurcateChain(header);
                chainList.add(newChain);
                return true;
            }
        }
        if (this.chainList.size() > 0) {
            System.out.println();
        }
        BlockHeaderChain chain = new BlockHeaderChain();
        chain.addHeader(header);
        chainList.add(chain);
        return true;
    }

    public void removeHash(String hash) {
        if (chainList.isEmpty()) {
            return;
        }
        List<BlockHeaderChain> tempList = new ArrayList<>(this.chainList);
        tempList.forEach((BlockHeaderChain chain) -> removeBlock(chain, hash));
    }

    private void removeBlock(BlockHeaderChain chain, String hashHex) {
        HeaderDigest hd = chain.getHeaderDigest(hashHex);
        if (hd == null) {
            return;
        }
        chain.removeHeaderDigest(hashHex);
        if (chain.size() == 0) {
            this.chainList.remove(chain);
        }
    }

    public List<String> getAllHashList(long height) {
        Set<String> set = new HashSet<>();
        List<BlockHeaderChain> chainList1 = new ArrayList<>(this.chainList);
        for (BlockHeaderChain chain : chainList1) {
            HeaderDigest headerDigest = chain.getHeaderDigest(height);
            if (null != headerDigest) {
                set.add(headerDigest.getHash());
            }
        }
        return new ArrayList<>(set);
    }

    public String getBlockHash(long height) {
        if (null == approvingChain) {
            return null;
        }
        HeaderDigest headerDigest = approvingChain.getHeaderDigest(height);
        if (null != headerDigest) {
            return headerDigest.getHash();
        }
        return null;
    }

    public boolean processing(long height) {
        if (chainList.isEmpty()) {
            return false;
        }
        this.checkIt();
        if (null == approvingChain) {
            return false;
        }
        Set<HeaderDigest> removeHashSet = new HashSet<>();
        for (int i = chainList.size() - 1; i >= 0; i--) {
            BlockHeaderChain chain = chainList.get(i);
            if (chain.size() < (approvingChain.size() - 6)) {
                removeHashSet.addAll(chain.getHeaderDigestList());
                this.chainList.remove(chain);
            }
        }

//        for (HeaderDigest hd : removeHashSet) {
//            if (!approvingChain.contains(hd)) {
//                Block block = confirmingBlockCacheManager.getBlock(hd.getHash());
//                confirmingBlockCacheManager.removeBlock(hd.getHash());
//                blockCacheBuffer.cacheBlock(block);
//            }
//        }

        if (approvingChain.getLastHd() != null && approvingChain.getLastHd().getHeight() >= (height + PocConsensusConstant.CONFIRM_BLOCK_COUNT)) {
            return true;
        }
        return false;
    }

    public int getHashSize() {
        Set<String> hashSet = new HashSet<>();
        for (BlockHeaderChain chain : chainList) {
            hashSet.addAll(chain.getHashSet());
        }
        return hashSet.size();
    }

    public int getChainSize() {
        return chainList.size();
    }

    public long getMaxHeight() {
        return maxHeight;
    }

    public BlockHeaderChain getApprovingChain() {
        return this.approvingChain;
    }

    public void clear() {
        this.chainList.clear();
        this.approvingChain = null;
        this.maxHeight = 0;
    }

    public void rollbackHash(String hash) {
        for (BlockHeaderChain chain : chainList) {
            chain.rollbackHeaderDigest(hash);
        }
    }
}