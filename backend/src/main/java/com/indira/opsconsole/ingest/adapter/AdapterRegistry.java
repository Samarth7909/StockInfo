package com.indira.opsconsole.ingest.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Spring-managed registry of all StreamAdapter implementations.
 * Adapters are injected automatically; adding a new adapter only requires
 * creating a @Component that implements StreamAdapter.
 */
@Component
@RequiredArgsConstructor
public class AdapterRegistry {

    private final List<StreamAdapter> adapters;

    private Map<String, StreamAdapter> byStreamName;

    @jakarta.annotation.PostConstruct
    void init() {
        byStreamName = adapters.stream()
            .collect(Collectors.toMap(StreamAdapter::streamName, Function.identity()));
    }

    public Optional<StreamAdapter> find(String streamName) {
        return Optional.ofNullable(byStreamName.get(streamName));
    }

    public List<String> knownStreamNames() {
        return List.copyOf(byStreamName.keySet());
    }
}
