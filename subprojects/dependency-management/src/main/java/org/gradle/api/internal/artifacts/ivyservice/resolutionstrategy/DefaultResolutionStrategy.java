/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy;

import org.gradle.api.Action;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.cache.ResolutionRules;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal;
import org.gradle.api.internal.artifacts.configurations.MutationValidator;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.dsl.ModuleVersionSelectorParsers;
import org.gradle.internal.Actions;
import org.gradle.internal.typeconversion.NormalizedTimeUnit;
import org.gradle.internal.typeconversion.TimeUnitsParser;
import org.gradle.util.DeprecationLogger;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.gradle.api.internal.artifacts.configurations.MutationValidator.MutationType.STRATEGY;
import static org.gradle.util.GUtil.flattenElements;

public class DefaultResolutionStrategy implements ResolutionStrategyInternal {
    private final Set<ModuleVersionSelector> forcedModules = new LinkedHashSet<ModuleVersionSelector>();
    private ConflictResolution conflictResolution = new LatestConflictResolution();
    private final DefaultComponentSelectionRules componentSelectionRules = new DefaultComponentSelectionRules();

    private final DefaultCachePolicy cachePolicy;
    private final DependencySubstitutionsInternal dependencySubstitutions;
    private MutationValidator mutationValidator = MutationValidator.IGNORE;

    public DefaultResolutionStrategy() {
        this(new DefaultCachePolicy(), new DefaultDependencySubstitutions());
    }

    DefaultResolutionStrategy(DefaultCachePolicy cachePolicy, DependencySubstitutionsInternal dependencySubstitutions) {
        this.cachePolicy = cachePolicy;
        this.dependencySubstitutions = dependencySubstitutions;
    }

    @Override
    public void beforeChange(MutationValidator validator) {
        mutationValidator = validator;
        cachePolicy.beforeChange(validator);
        componentSelectionRules.beforeChange(validator);
        dependencySubstitutions.beforeChange(validator);
    }

    public Set<ModuleVersionSelector> getForcedModules() {
        return Collections.unmodifiableSet(forcedModules);
    }

    public ResolutionStrategy failOnVersionConflict() {
        mutationValidator.validateMutation(STRATEGY);
        this.conflictResolution = new StrictConflictResolution();
        return this;
    }

    public ConflictResolution getConflictResolution() {
        return this.conflictResolution;
    }

    public ResolutionRules getResolutionRules() {
        return cachePolicy;
    }

    public DefaultResolutionStrategy force(Object... moduleVersionSelectorNotations) {
        mutationValidator.validateMutation(STRATEGY);
        Set<ModuleVersionSelector> modules = ModuleVersionSelectorParsers.multiParser().parseNotation(moduleVersionSelectorNotations);
        this.forcedModules.addAll(modules);
        return this;
    }

    @SuppressWarnings("deprecation")
    public ResolutionStrategy eachDependency(Action<? super DependencyResolveDetails> rule) {
        DeprecationLogger.nagUserOfReplacedMethod("ResolutionStrategy.eachDependency()", "DependencySubstitution.eachModule()");
        mutationValidator.validateMutation(STRATEGY);
        dependencySubstitutions.allWithDependencyResolveDetails(rule);
        return this;
    }

    public Action<DependencySubstitution<ComponentSelector>> getDependencySubstitutionRule() {
        Collection<Action<DependencySubstitution<ComponentSelector>>> allRules = flattenElements(new ModuleForcingResolveRule(forcedModules), dependencySubstitutions.getDependencySubstitutionRule());
        return Actions.composite(allRules);
    }

    public DefaultResolutionStrategy setForcedModules(Object ... moduleVersionSelectorNotations) {
        mutationValidator.validateMutation(STRATEGY);
        Set<ModuleVersionSelector> modules = ModuleVersionSelectorParsers.multiParser().parseNotation(moduleVersionSelectorNotations);
        this.forcedModules.clear();
        this.forcedModules.addAll(modules);
        return this;
    }

    public DefaultCachePolicy getCachePolicy() {
        return cachePolicy;
    }

    public void cacheDynamicVersionsFor(int value, String units) {
        NormalizedTimeUnit timeUnit = new TimeUnitsParser().parseNotation(units, value);
        cacheDynamicVersionsFor(timeUnit.getValue(), timeUnit.getTimeUnit());
    }

    public void cacheDynamicVersionsFor(int value, TimeUnit units) {
        this.cachePolicy.cacheDynamicVersionsFor(value, units);
    }

    public void cacheChangingModulesFor(int value, String units) {
        NormalizedTimeUnit timeUnit = new TimeUnitsParser().parseNotation(units, value);
        cacheChangingModulesFor(timeUnit.getValue(), timeUnit.getTimeUnit());
    }

    public void cacheChangingModulesFor(int value, TimeUnit units) {
        this.cachePolicy.cacheChangingModulesFor(value, units);
    }

    public ComponentSelectionRulesInternal getComponentSelection() {
        return componentSelectionRules;
    }

    public ResolutionStrategy componentSelection(Action<? super ComponentSelectionRules> action) {
        action.execute(componentSelectionRules);
        return this;
    }

    @Override
    public DependencySubstitutionsInternal getDependencySubstitution() {
        return dependencySubstitutions;
    }

    @Override
    public ResolutionStrategy dependencySubstitution(Action<? super DependencySubstitutions> action) {
        action.execute(dependencySubstitutions);
        return this;
    }

    public DefaultResolutionStrategy copy() {
        DefaultResolutionStrategy out = new DefaultResolutionStrategy(cachePolicy.copy(), dependencySubstitutions.copy());

        if (conflictResolution instanceof StrictConflictResolution) {
            out.failOnVersionConflict();
        }
        out.setForcedModules(getForcedModules());
        out.getComponentSelection().getRules().addAll(componentSelectionRules.getRules());
        return out;
    }
}
