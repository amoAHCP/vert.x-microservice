package org.jacpfx.vertx.entrypoint;

import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by amo on 10.11.14.
 */
public class CustomRouteMatcher implements Handler<HttpServerRequest> {

    private final List<PatternBinding> getBindings = new ArrayList<>();
    private final List<PatternBinding> putBindings = new ArrayList<>();
    private final List<PatternBinding> postBindings = new ArrayList<>();
    private final List<PatternBinding> deleteBindings = new ArrayList<>();
    private final List<PatternBinding> optionsBindings = new ArrayList<>();
    private final List<PatternBinding> headBindings = new ArrayList<>();
    private final List<PatternBinding> traceBindings = new ArrayList<>();
    private final List<PatternBinding> connectBindings = new ArrayList<>();
    private final List<PatternBinding> patchBindings = new ArrayList<>();
    private Handler<HttpServerRequest> noMatchHandler;

    @Override
    public void handle(HttpServerRequest request) {
        switch (request.method()) {
            case "GET":
                route(request, getBindings);
                break;
            case "PUT":
                route(request, putBindings);
                break;
            case "POST":
                route(request, postBindings);
                break;
            case "DELETE":
                route(request, deleteBindings);
                break;
            case "OPTIONS":
                route(request, optionsBindings);
                break;
            case "HEAD":
                route(request, headBindings);
                break;
            case "TRACE":
                route(request, traceBindings);
                break;
            case "PATCH":
                route(request, patchBindings);
                break;
            case "CONNECT":
                route(request, connectBindings);
                break;
            default:
                notFound(request);
        }
    }


    /**
     * Removes a binding for an URL pattern
     * @param pattern
     * @return
     */
    public CustomRouteMatcher removeAll(String pattern) {
        removePattern(pattern,getBindings);
        removePattern(pattern,putBindings);
        removePattern(pattern,postBindings);
        removePattern(pattern,deleteBindings);
        removePattern(pattern,optionsBindings);
        removePattern(pattern,headBindings);
        removePattern(pattern,traceBindings);
        removePattern(pattern,patchBindings);
        removePattern(pattern,connectBindings);
        return this;
    }

    private void removePattern(String pattern,List<PatternBinding> bindings) {
        final Optional<PatternBinding> any = bindings.stream().filter(binding -> binding.origPattern.equals(pattern)).findAny();
        any.ifPresent(patternBinding->{
            bindings.remove(patternBinding);
        });
    }

    /**
     * Specify a handler that will be called for a matching HTTP GET
     * @param pattern The simple pattern
     * @param handler The handler to call
     */
    public CustomRouteMatcher get(String pattern, Handler<HttpServerRequest> handler) {
        addPattern(pattern, handler, getBindings);
        return this;
    }

    /**
     * Specify a handler that will be called for a matching HTTP PUT
     * @param pattern The simple pattern
     * @param handler The handler to call
     */
    public CustomRouteMatcher put(String pattern, Handler<HttpServerRequest> handler) {
        addPattern(pattern, handler, putBindings);
        return this;
    }

    /**
     * Specify a handler that will be called for a matching HTTP POST
     * @param pattern The simple pattern
     * @param handler The handler to call
     */
    public CustomRouteMatcher post(String pattern, Handler<HttpServerRequest> handler) {
        addPattern(pattern, handler, postBindings);
        return this;
    }

    /**
     * Specify a handler that will be called for a matching HTTP DELETE
     * @param pattern The simple pattern
     * @param handler The handler to call
     */
    public CustomRouteMatcher delete(String pattern, Handler<HttpServerRequest> handler) {
        addPattern(pattern, handler, deleteBindings);
        return this;
    }

    /**
     * Specify a handler that will be called for a matching HTTP OPTIONS
     * @param pattern The simple pattern
     * @param handler The handler to call
     */
    public CustomRouteMatcher options(String pattern, Handler<HttpServerRequest> handler) {
        addPattern(pattern, handler, optionsBindings);
        return this;
    }

    /**
     * Specify a handler that will be called for a matching HTTP HEAD
     * @param pattern The simple pattern
     * @param handler The handler to call
     */
    public CustomRouteMatcher head(String pattern, Handler<HttpServerRequest> handler) {
        addPattern(pattern, handler, headBindings);
        return this;
    }

    /**
     * Specify a handler that will be called for a matching HTTP TRACE
     * @param pattern The simple pattern
     * @param handler The handler to call
     */
    public CustomRouteMatcher trace(String pattern, Handler<HttpServerRequest> handler) {
        addPattern(pattern, handler, traceBindings);
        return this;
    }

    /**
     * Specify a handler that will be called for a matching HTTP CONNECT
     * @param pattern The simple pattern
     * @param handler The handler to call
     */
    public CustomRouteMatcher connect(String pattern, Handler<HttpServerRequest> handler) {
        addPattern(pattern, handler, connectBindings);
        return this;
    }

    /**
     * Specify a handler that will be called for a matching HTTP PATCH
     * @param pattern The simple pattern
     * @param handler The handler to call
     */
    public CustomRouteMatcher patch(String pattern, Handler<HttpServerRequest> handler) {
        addPattern(pattern, handler, patchBindings);
        return this;
    }

    /**
     * Specify a handler that will be called for all HTTP methods
     * @param pattern The simple pattern
     * @param handler The handler to call
     */
    public CustomRouteMatcher all(String pattern, Handler<HttpServerRequest> handler) {
        addPattern(pattern, handler, getBindings);
        addPattern(pattern, handler, putBindings);
        addPattern(pattern, handler, postBindings);
        addPattern(pattern, handler, deleteBindings);
        addPattern(pattern, handler, optionsBindings);
        addPattern(pattern, handler, headBindings);
        addPattern(pattern, handler, traceBindings);
        addPattern(pattern, handler, connectBindings);
        addPattern(pattern, handler, patchBindings);
        return this;
    }

    /**
     * Specify a handler that will be called for a matching HTTP GET
     * @param regex A regular expression
     * @param handler The handler to call
     */
    public CustomRouteMatcher getWithRegEx(String regex, Handler<HttpServerRequest> handler) {
        addRegEx(regex, handler, getBindings);
        return this;
    }

    /**
     * Specify a handler that will be called for a matching HTTP PUT
     * @param regex A regular expression
     * @param handler The handler to call
     */
    public CustomRouteMatcher putWithRegEx(String regex, Handler<HttpServerRequest> handler) {
        addRegEx(regex, handler, putBindings);
        return this;
    }

    /**
     * Specify a handler that will be called for a matching HTTP POST
     * @param regex A regular expression
     * @param handler The handler to call
     */
    public CustomRouteMatcher postWithRegEx(String regex, Handler<HttpServerRequest> handler) {
        addRegEx(regex, handler, postBindings);
        return this;
    }

    /**
     * Specify a handler that will be called for a matching HTTP DELETE
     * @param regex A regular expression
     * @param handler The handler to call
     */
    public CustomRouteMatcher deleteWithRegEx(String regex, Handler<HttpServerRequest> handler) {
        addRegEx(regex, handler, deleteBindings);
        return this;
    }

    /**
     * Specify a handler that will be called for a matching HTTP OPTIONS
     * @param regex A regular expression
     * @param handler The handler to call
     */
    public CustomRouteMatcher optionsWithRegEx(String regex, Handler<HttpServerRequest> handler) {
        addRegEx(regex, handler, optionsBindings);
        return this;
    }

    /**
     * Specify a handler that will be called for a matching HTTP HEAD
     * @param regex A regular expression
     * @param handler The handler to call
     */
    public CustomRouteMatcher headWithRegEx(String regex, Handler<HttpServerRequest> handler) {
        addRegEx(regex, handler, headBindings);
        return this;
    }

    /**
     * Specify a handler that will be called for a matching HTTP TRACE
     * @param regex A regular expression
     * @param handler The handler to call
     */
    public CustomRouteMatcher traceWithRegEx(String regex, Handler<HttpServerRequest> handler) {
        addRegEx(regex, handler, traceBindings);
        return this;
    }

    /**
     * Specify a handler that will be called for a matching HTTP CONNECT
     * @param regex A regular expression
     * @param handler The handler to call
     */
    public CustomRouteMatcher connectWithRegEx(String regex, Handler<HttpServerRequest> handler) {
        addRegEx(regex, handler, connectBindings);
        return this;
    }

    /**
     * Specify a handler that will be called for a matching HTTP PATCH
     * @param regex A regular expression
     * @param handler The handler to call
     */
    public CustomRouteMatcher patchWithRegEx(String regex, Handler<HttpServerRequest> handler) {
        addRegEx(regex, handler, patchBindings);
        return this;
    }

    /**
     * Specify a handler that will be called for all HTTP methods
     * @param regex A regular expression
     * @param handler The handler to call
     */
    public CustomRouteMatcher allWithRegEx(String regex, Handler<HttpServerRequest> handler) {
        addRegEx(regex, handler, getBindings);
        addRegEx(regex, handler, putBindings);
        addRegEx(regex, handler, postBindings);
        addRegEx(regex, handler, deleteBindings);
        addRegEx(regex, handler, optionsBindings);
        addRegEx(regex, handler, headBindings);
        addRegEx(regex, handler, traceBindings);
        addRegEx(regex, handler, connectBindings);
        addRegEx(regex, handler, patchBindings);
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


    private static void addPattern(String input, Handler<HttpServerRequest> handler, List<PatternBinding> bindings) {
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
        PatternBinding binding = new PatternBinding(Pattern.compile(regex), input,groups, handler);
        bindings.add(binding);
    }

    private static void addRegEx(String input, Handler<HttpServerRequest> handler, List<PatternBinding> bindings) {
        PatternBinding binding = new PatternBinding(Pattern.compile(input), input,null, handler);
        bindings.add(binding);
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
                request.params().add(params);
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