package com.hcmut.emobility.m8simulation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcmut.emobility.m8simulation.domain.SimulationModels.Run;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Local persistence until TV4 provides the approved M8 schema. Atomic replacement on save. */
@Service
public class RunStore {
    private final ObjectMapper mapper; private final Path file;
    private final LinkedHashMap<String,Run> runs=new LinkedHashMap<>();
    public RunStore(ObjectMapper mapper,@Value("${app.m8.store-file:.local/m8/runs.json}") String path) throws IOException {
        this.mapper=mapper; this.file=Path.of(path).toAbsolutePath();
        if (Files.exists(file)) for (Run r:mapper.readValue(file.toFile(),Run[].class)) runs.put(r.id(),r);
    }
    public synchronized List<Run> list() { var list=new ArrayList<>(runs.values()); Collections.reverse(list); return List.copyOf(list); }
    public synchronized Run get(String id) { Run run=runs.get(id); if(run==null) throw new NoSuchElementException("Run not found"); return run; }
    public synchronized void save(Run run) throws IOException {
        var next=new LinkedHashMap<>(runs); next.put(run.id(),run);
        if(next.size()>200) next.remove(next.keySet().iterator().next());
        Files.createDirectories(file.getParent());
        Path tmp=Files.createTempFile(file.getParent(),"runs-",".tmp");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(),next.values());
            try { Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e) { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING); }
            runs.clear(); runs.putAll(next);
        } finally { Files.deleteIfExists(tmp); }
    }
}
