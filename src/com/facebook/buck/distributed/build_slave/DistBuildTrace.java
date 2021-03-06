/*
 * Copyright 2017-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.distributed.build_slave;

import com.facebook.buck.distributed.thrift.StampedeId;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Trace of rule execution history per minion, from the coordinator's point of view. Generated by
 * {@link DistBuildTraceTracker}.
 */
public class DistBuildTrace {

  /** Single build rule information. */
  public static class RuleTrace {
    public final String ruleName;
    public final long startEpochMillis;
    public final long finishEpochMillis;

    public RuleTrace(String ruleName, long startEpochMillis, long finishEpochMillis) {
      this.ruleName = ruleName;
      this.startEpochMillis = startEpochMillis;
      this.finishEpochMillis = finishEpochMillis;
    }
  }

  /** List of rules executed on a single thread in a minion. */
  public static class MinionThread {
    public final List<RuleTrace> ruleTraces = new ArrayList<>();
  }

  /** List of rules executed per thread in a single minion. */
  public static class MinionTrace {
    public final String minionId;
    public final List<MinionThread> threads = new ArrayList<>();

    MinionTrace(String minionId) {
      this.minionId = minionId;
    }

    void recordItems(List<RuleTrace> rules) {
      rules
          .stream()
          .sorted(Comparator.comparingLong(rule -> rule.startEpochMillis))
          .sequential()
          .forEach(
              rule -> {
                for (MinionThread thread : threads) {
                  RuleTrace lastRuleInThread = thread.ruleTraces.get(thread.ruleTraces.size() - 1);
                  if (lastRuleInThread.finishEpochMillis <= rule.startEpochMillis) {
                    thread.ruleTraces.add(rule);
                    return;
                  }
                }
                MinionThread newThread = new MinionThread();
                newThread.ruleTraces.add(rule);
                threads.add(newThread);
              });
    }
  }

  public final StampedeId stampedeId;
  public final List<MinionTrace> minions;

  public DistBuildTrace(StampedeId stampedeId, Map<String, List<RuleTrace>> rulesByMinionId) {
    this.stampedeId = stampedeId;
    this.minions = guessMinionThreadAssignment(rulesByMinionId);
  }

  private static List<MinionTrace> guessMinionThreadAssignment(
      Map<String, List<RuleTrace>> rulesByMinionId) {
    List<MinionTrace> minionTraces = new ArrayList<>(rulesByMinionId.size());

    rulesByMinionId
        .keySet()
        .forEach(
            minion -> {
              MinionTrace minionTrace = new MinionTrace(minion);
              minionTrace.recordItems(rulesByMinionId.get(minion));
              minionTraces.add(minionTrace);
            });

    return minionTraces;
  }

  /** Write trace in chrome trace format. */
  public void dumpToChromeTrace(Path chromeTrace) throws IOException {
    DistBuildChromeTraceRenderer.render(this, chromeTrace);
  }
}
