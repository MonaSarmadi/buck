/*
 * Copyright 2018-present Facebook, Inc.
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

package com.facebook.buck.rules.modern.builders;

import com.facebook.buck.rules.BuildExecutorRunner;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRuleStrategy;
import com.facebook.buck.rules.Cell;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.modern.config.ModernBuildRuleConfig;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.Optional;

/**
 * Constructs various CustomBuildRuleStrategies for ModernBuildRules based on the
 * modern_build_rule.strategy config option.
 */
public class ModernBuildRuleBuilderFactory {
  /** Creates a BuildRuleStrategy for ModernBuildRules based on the buck configuration. */
  public static Optional<BuildRuleStrategy> getBuildStrategy(
      ModernBuildRuleConfig config,
      BuildRuleResolver resolver,
      Cell rootCell,
      CellPathResolver cellResolver) {
    // Mark these as used. We'll use them as we add new strategies.
    resolver.getClass();
    rootCell.getClass();

    switch (config.getBuildStrategy()) {
      case NONE:
        return Optional.empty();
      case DEBUG_RECONSTRUCT:
        return Optional.of(
            ModernBuildRuleBuilderFactory.createReconstructing(
                new SourcePathRuleFinder(resolver), cellResolver, rootCell));
      case DEBUG_PASSTHROUGH:
        return Optional.of(ModernBuildRuleBuilderFactory.createPassthrough());
    }
    throw new IllegalStateException(
        "Unrecognized build strategy " + config.getBuildStrategy() + ".");
  }

  /** The passthrough strategy just forwards to executorRunner.runWithDefaultExecutor. */
  public static BuildRuleStrategy createPassthrough() {
    return new AbstractModernBuildRuleStrategy() {
      @Override
      public void build(
          ListeningExecutorService service, BuildRule rule, BuildExecutorRunner executorRunner) {
        service.execute(executorRunner::runWithDefaultExecutor);
      }
    };
  }

  /**
   * The reconstructing strategy serializes and deserializes the build rule in memory and builds the
   * deserialized version.
   */
  public static BuildRuleStrategy createReconstructing(
      SourcePathRuleFinder ruleFinder, CellPathResolver cellResolver, Cell rootCell) {
    return new ReconstructingStrategy(ruleFinder, cellResolver, rootCell);
  }
}
