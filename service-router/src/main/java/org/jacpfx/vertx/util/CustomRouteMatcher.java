package org.jacpfx.vertx.util;


import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by amo on 10.11.14.
 */
public class CustomRouteMatcher  {

    private final Map<HttpMethod, List<PatternBinding>> bindingsMap = new HashMap<>();

    private Handler<HttpServerRequest> noMatchHandler;

    /**
     * Do not instantiate this directly - use RouteMatcher.newRouteMatcher() instead
     */
    public CustomRouteMatcher() {
    }

    public CustomRouteMatcher accept(HttpServerRequest request) {
        List<PatternBinding> bindings = bindingsMap.get(request.method());
        if (bindings != null) {
            route(request, bindings);
        } else {
            notFound(request);
        }
        return this;
    }

    public CustomRouteMatcher matchMethod(HttpMethod method, String pattern, Handler<HttpServerRequest> handler) {
        addPattern(method, pattern, handler);
        return this;
    }

    public CustomRouteMatcher matchMethodWithRegEx(HttpMethod method, String regex, Handler<HttpServerRequest> handler) {
        addRegEx(method, regex, handler);
        return this;
    }

    /**
     * Removes a binding for an URL pattern
     * @param pattern
     * @return
     */
    public CustomRouteMatcher removeAll(String pattern) {
        removePattern(pattern,getBindings(HttpMethod.GET));
        removePattern(pattern,getBindings(HttpMethod.PUT));
        removePattern(pattern,getBindings(HttpMethod.POST));
        removePattern(pattern,getBindings(HttpMethod.DELETE));
        removePattern(pattern,getBindings(HttpMethod.OPTIONS));
        removePattern(pattern,getBindings(HttpMethod.HEAD));
        removePattern(pattern,getBindings(HttpMethod.TRACE));
        removePattern(pattern,getBindings(HttpMethod.PATCH));
        removePattern(pattern,getBindings(HttpMethod.CONNECT));
        return this;
    }

    private void removePattern(String pattern,List<PatternBinding> bindings) {
        final Optional<PatternBinding> any = bindings.stream().filter(binding -> binding.origPattern.equals(pattern)).findAny();
        any.ifPresent(patternBinding->{
            bindings.remove(patternBinding);
        });
    }

    /**
     * Specify a handler that will be called for all HTTP methods
     * @param pattern The simple pattern
     * @param handler The handler to call
     */
    public CustomRouteMatcher all(String pattern, Handler<HttpServerRequest> handler) {
        addPattern(HttpMethod.GET, pattern, handler);
        addPattern(HttpMethod.PUT, pattern, handler);
        addPattern(HttpMethod.POST, pattern, handler);
        addPattern(HttpMethod.DELETE, pattern, handler);
        addPattern(HttpMethod.OPTIONS, pattern, handler);
        addPattern(HttpMethod.HEAD, pattern, handler);
        addPattern(HttpMethod.TRACE, pattern, handler);
        addPattern(HttpMethod.CONNECT, pattern, handler);
        addPattern(HttpMethod.PATCH, pattern, handler);
        return this;
    }

    /**
     * Specify a handler that will be called for all HTTP methods
     * @param regex A regular expression
     * @param handler The handler to call
     */
    public CustomRouteMatcher allWithRegEx(String regex, Handler<HttpServerRequest> handler) {
        addRegEx(HttpMethod.GET, regex, handler);
        addRegEx(HttpMethod.PUT, regex, handler);
        addRegEx(HttpMethod.POST, regex, handler);
        addRegEx(HttpMethod.DELETE, regex, handler);
        addRegEx(HttpMethod.OPTIONS, regex, handler);
        addRegEx(HttpMethod.HEAD, regex, handler);
        addRegEx(HttpMethod.TRACE, regex, handler);
        addRegEx(HttpMethod.CONNECT, regex, handler);
        addRegEx(HttpMethod.PATCH, regex, handler);
        return this;
    }

    /**
     * Specify a handler that will be called when no other handlers match.
     * If this handler is not specified default behaviour is to return a 404
     */
    public CustomRouteMatcher noMatch(Handler<HttpServerRequest> handler) {
        noMatchHandler = handler;
        return this;
    }

    private void addPattern(HttpMethod method, String input, Handler<HttpServerRequest> handler) {
        List<PatternBinding> bindings = getBindings(method);
        // We need to search for any :<token name> tokens in the String and replace them with named capture groups
        Matcher m =  Pattern.compile(":([A-Za-z][A-Za-z0-9_]*)").matcher(input);
        StringBuffer sb = new StringBuffer();
        Set<String> groups = new HashSet<>();
        while (m.find()) {
            String group = m.group().substring(1);
            if (groups.contains(group)) {
                throw new IllegalArgumentException("Cannot use identifier " + group + " more than once in pattern string");
            }
            m.appendReplacement(sb, "(?<$1>[^\\/]+)");
            groups.add(group);
        }
        m.appendTail(sb);
        String regex = sb.toString();
        PatternBinding binding = new PatternBinding(Pattern.compile(regex),regex, groups, handler);
        bindings.add(binding);
    }

    private void addRegEx(HttpMethod method, String input, Handler<HttpServerRequest> handler) {
        List<PatternBinding> bindings = getBindings(method);
        PatternBinding binding = new PatternBinding(Pattern.compile(input),input, null, handler);
        bindings.add(binding);
    }

    private List<PatternBinding> getBindings(HttpMethod method) {
        List<PatternBinding> bindings = bindingsMap.get(method);
        if (bindings == null) {
            bindings = new ArrayList<>();
            bindingsMap.put(method, bindings);
        }
        return bindings;
    }

    private void route(HttpServerRequest request, List<PatternBinding> bindings) {
        for (PatternBinding binding: bindings) {
            Matcher m = binding.pattern.matcher(request.path());
            if (m.matches()) {
                Map<String, String> params = new HashMap<>(m.groupCount());
                if (binding.paramNames != null) {
                    // Named params
                    for (String param: binding.paramNames) {
                        params.put(param, m.group(param));
                    }
                } else {
                    // Un-named params
                    for (int i = 0; i < m.groupCount(); i++) {
                        params.put("param" + i, m.group(i + 1));
                    }
                }
                request.params().addAll(params);
                binding.handler.handle(request);
                return;
            }
        }
        notFound(request);
    }

    private void notFound(HttpServerRequest request) {
        if (noMatchHandler != null) {
            noMatchHandler.handle(request);
        } else {
            // Default 404
            request.response().setStatusCode(404);
            request.response().end();
        }
    }



    private static class PatternBinding {
        final Pattern pattern;
        final Handler<HttpServerRequest> handler;
        final Set<String> paramNames;
        final String origPattern;

        private PatternBinding(Pattern pattern,String origPattern, Set<String> paramNames, Handler<HttpServerRequest> handler) {
            this.pattern = pattern;
            this.paramNames = paramNames;
            this.handler = handler;
            this.origPattern = origPattern;
        }
    }

}
