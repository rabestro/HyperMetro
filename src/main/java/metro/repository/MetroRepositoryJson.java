package metro.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import metro.algorithm.Graph;
import metro.model.Station;
import metro.model.StationId;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Repository
public class MetroRepositoryJson implements MetroRepository, InitializingBean {

    private static final TypeReference<Map<String, Deque<Station>>> SCHEMA_TYPE = new TypeReference<>() {
    };

    private Map<String, Deque<Station>> metroMap;

    @Value("${hypermetro.time:5}")
    private int transferTime;

    @Value("#{T(java.nio.file.Path).of(systemProperties['hypermetro.file'])}")
    private Path schema;

    @Override
    public void afterPropertiesSet() throws Exception {
        metroMap = new JsonMapper().readValue(schema.toFile(), SCHEMA_TYPE);
    }

    @Override
    public Deque<Station> findLine(String line) {
        return metroMap.get(line);
    }

    @Override
    public void addHead(String line, String station, int time) {
        var metroLine = getLine(line);
        var next = new HashSet<String>();
        if (metroLine.isEmpty()) {
            metroLine.add(new Station(station, time, next, new HashSet<>(), new HashSet<>()));
        } else {
            next.add(metroLine.getFirst().name());
            metroLine.addFirst(new Station(station, time, next, metroLine.getFirst().prev(), new HashSet<>()));
        }
    }

    private Deque<Station> getLine(String line) {
        return Objects.requireNonNull(metroMap.get(line), () -> "There is no metro line with the name " + line);
    }

    @Override
    public void append(String line, String station, int time) {
        var metroLine = getLine(line);
        var prev = new HashSet<String>();
        if (metroLine.isEmpty()) {
            metroLine.add(new Station(station, time, new HashSet<>(), prev, new HashSet<>()));
        } else {
            prev.add(metroLine.getLast().name());
            metroLine.addFirst(new Station(station, time, metroLine.getLast().next(), prev, new HashSet<>()));
        }
    }

    @Override
    public void remove(String line, String station) {
        throw new UnsupportedOperationException("This operation has not yet been implemented");
    }

    @Override
    public void connect(String sourceLine, String sourceStation, String targetLine, String targetStation) {
        var source = getStation(sourceLine, sourceStation);
        var target = getStation(targetLine, targetStation);
        source.transfer().add(new StationId(targetLine, targetStation));
        target.transfer().add(new StationId(sourceLine, sourceStation));
    }

    private Station getStation(String line, String station) {
        return getLine(line).stream()
                .filter(s -> s.name().equals(station))
                .findAny()
                .orElseThrow(() -> new NoSuchElementException(
                        "There is no station named “%s” on the metro line “%s”.".formatted(station, line)
                ));
    }

    @Override
    public Graph<StationId> getGraph() {
        var schema = new HashMap<StationId, Map<StationId, Number>>();
        for (var entry : metroMap.entrySet()) {
            var line = entry.getKey();
            for (var station : entry.getValue()) {
                var vertex = new StationId(line, station.name());
                var edges = new HashMap<StationId, Number>();
                station.transfer().forEach(target -> edges.put(target, transferTime));
                station.next().forEach(target -> edges.put(new StationId(line, target), station.time()));
                station.prev().forEach(target ->
                        edges.put(new StationId(line, target), getStation(line, target).time()));
                schema.put(vertex, edges);
            }
        }
        return Graph.of(schema);
    }

    @Override
    public String getMetroName() {
        var fileName = schema.getFileName().toString();
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }

}
