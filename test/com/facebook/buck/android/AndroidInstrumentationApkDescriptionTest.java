/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.android;

import static org.junit.Assert.assertThat;

import com.facebook.buck.jvm.java.JavaLibraryBuilder;
import com.facebook.buck.jvm.java.KeystoreBuilder;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.rules.TestBuildRuleResolver;
import com.facebook.buck.testutil.TargetGraphFactory;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Paths;
import org.hamcrest.Matchers;
import org.junit.Test;

public class AndroidInstrumentationApkDescriptionTest {

  @Test
  public void testNoDxRulesBecomeFirstOrderDeps() {
    // Build up the original APK rule.
    TargetNode<?, ?> transitiveDepNode =
        JavaLibraryBuilder.createBuilder(BuildTargetFactory.newInstance("//exciting:dep"))
            .addSrc(Paths.get("Dep.java"))
            .build();
    TargetNode<?, ?> dep =
        JavaLibraryBuilder.createBuilder(BuildTargetFactory.newInstance("//exciting:target"))
            .addSrc(Paths.get("Other.java"))
            .addDep(transitiveDepNode.getBuildTarget())
            .build();
    TargetNode<?, ?> keystore =
        KeystoreBuilder.createBuilder(BuildTargetFactory.newInstance("//:keystore"))
            .setStore(FakeSourcePath.of("store"))
            .setProperties(FakeSourcePath.of("properties"))
            .build();
    TargetNode<?, ?> androidBinary =
        AndroidBinaryBuilder.createBuilder(BuildTargetFactory.newInstance("//:apk"))
            .setManifest(FakeSourcePath.of("manifest.xml"))
            .setKeystore(keystore.getBuildTarget())
            .setNoDx(ImmutableSet.of(transitiveDepNode.getBuildTarget()))
            .setOriginalDeps(ImmutableSortedSet.of(dep.getBuildTarget()))
            .build();
    BuildTarget target = BuildTargetFactory.newInstance("//:rule");
    TargetNode<?, ?> androidInstrumentationApk =
        AndroidInstrumentationApkBuilder.createBuilder(target)
            .setManifest(FakeSourcePath.of("manifest.xml"))
            .setApk(androidBinary.getBuildTarget())
            .build();

    TargetGraph targetGraph =
        TargetGraphFactory.newInstance(
            transitiveDepNode, dep, keystore, androidBinary, androidInstrumentationApk);
    BuildRuleResolver ruleResolver = new TestBuildRuleResolver(targetGraph);
    BuildRule transitiveDep = ruleResolver.requireRule(transitiveDepNode.getBuildTarget());
    ruleResolver.requireRule(target);
    BuildRule nonPredexedRule =
        ruleResolver.requireRule(
            target.withFlavors(AndroidBinaryGraphEnhancer.NON_PREDEXED_DEX_BUILDABLE_FLAVOR));
    assertThat(nonPredexedRule.getBuildDeps(), Matchers.hasItem(transitiveDep));
  }
}
