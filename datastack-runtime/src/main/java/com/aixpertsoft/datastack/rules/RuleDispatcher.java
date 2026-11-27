package com.aixpertsoft.datastack.rules;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Finds every {@link Rule} bean and calls the matching ones when a DAO raises an event.
 * Rules are collected lazily on first use, because rules may inject DAOs and DAOs use this dispatcher.
 */
@Component
public class RuleDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RuleDispatcher.class);

    private final ApplicationContext context;
    private volatile List<RegisteredRule> rules;

    public RuleDispatcher(ApplicationContext context) {
        this.context = context;
    }

    public void fire(Event event, Object entity) {
        for (RegisteredRule rule : rules()) {
            if (rule.applies(event, entity)) {
                rule.invoke(new BeforeSave<>(event, entity));
            }
        }
    }

    /** All registered rules in execution order; used at start-up for logging and by tests. */
    public List<String> describe() {
        return rules().stream().map(RegisteredRule::toString).toList();
    }

    private List<RegisteredRule> rules() {
        List<RegisteredRule> result = rules;
        if (result == null) {
            synchronized (this) {
                if (rules == null) {
                    rules = collect();
                    rules.forEach(r -> log.info("Business rule registered: {}", r));
                }
                result = rules;
            }
        }
        return result;
    }

    private List<RegisteredRule> collect() {
        List<RegisteredRule> found = new ArrayList<>();
        context.getBeansWithAnnotation(Rule.class).values().forEach(bean -> {
            Class<?> type = AopUtils.getTargetClass(bean);
            Rule rule = type.getAnnotation(Rule.class);
            Method apply = Arrays.stream(type.getMethods())
                    .filter(m -> m.getName().equals("apply")
                              && m.getParameterCount() == 1
                              && m.getParameterTypes()[0] == BeforeSave.class)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            type.getName() + " is a @Rule but has no apply(BeforeSave) method"));
            found.add(new RegisteredRule(type.getSimpleName(), rule, bean, apply));
        });
        found.sort(Comparator.comparingInt((RegisteredRule r) -> r.rule.order()).thenComparing(r -> r.name));
        return List.copyOf(found);
    }

    private record RegisteredRule(String name, Rule rule, Object bean, Method apply) {

        boolean applies(Event event, Object entity) {
            return rule.on().isInstance(entity) && Arrays.asList(rule.events()).contains(event);
        }

        void invoke(BeforeSave<?> event) {
            try {
                apply.invoke(bean, event);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("Rule " + name + " failed", e.getCause());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Rule " + name + " is not accessible", e);
            }
        }

        @Override
        public String toString() {
            return name + " on " + rule.on().getSimpleName() + " " + Arrays.toString(rule.events())
                    + " order " + rule.order();
        }
    }
}
