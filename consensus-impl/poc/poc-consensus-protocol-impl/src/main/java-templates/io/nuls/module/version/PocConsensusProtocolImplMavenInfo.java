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

package io.nuls.module.version;

import io.nuls.core.model.intf.NulsVersion;
import io.nuls.core.utils.spring.lite.annotation.MavenInfo;

/**
 * @author: Niels Wang
 * @date: 2018/3/1
 */
@MavenInfo
public class PocConsensusProtocolImplMavenInfo implements NulsVersion {

    public static final String VERSION = "${project.version}";
    public static final String GROUP_ID = "${project.groupId}";
    public static final String ARTIFACT_ID = "${project.artifactId}";

    public String getVersion() {
        return VERSION;
    }

    public String getArtifactId() {
        return ARTIFACT_ID;
    }

    public String getGroupId(){
        return GROUP_ID;
    }


}
