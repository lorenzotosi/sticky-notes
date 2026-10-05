// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.infrastructure;

import com.mongodb.client.result.UpdateResult;
import java.util.Optional;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.ReplaceOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import stickynotes.application.BoardRepository;
import stickynotes.contract.BoardSnapshot;

@Repository
public class MongoBoardRepository implements BoardRepository {

    private final MongoTemplate mongoTemplate;

    public MongoBoardRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<BoardSnapshot> load() {
        BoardDocument document = mongoTemplate.findById("main", BoardDocument.class);

        if (document == null) {
            return Optional.empty();
        }

        BoardSnapshot snapshot = BoardDocumentMapper.toSnapshot(document);

        return Optional.of(snapshot);
    }

    @Override
    public Optional<BoardSnapshot> save(BoardSnapshot updated, int expectedRevision) {

        if (!"main".equals(updated.getId())) {
            throw new IllegalArgumentException("Only board main can be saved");
        }

        if (expectedRevision < 0 || updated.getRevision() != expectedRevision) {
            throw new IllegalArgumentException("Expected revision must be non-negative and match the snapshot");
        }

        int nextRevision = Math.addExact(expectedRevision, 1);

        BoardSnapshot toSave = new BoardSnapshot(
                updated.getId(), updated.getSchemaVersion(), nextRevision, updated.getWipLimit(), updated.getNotes());

        BoardDocument document = BoardDocumentMapper.toDocument(toSave);

        Query query =
                Query.query(Criteria.where("_id").is("main").and("revision").is(expectedRevision));

        UpdateResult result = mongoTemplate.replace(query, document, ReplaceOptions.none(), "boards");

        if (!result.wasAcknowledged()) {
            throw new IllegalStateException("MongoDB did not acknowledge the board save");
        }

        if (result.getMatchedCount() == 0) {
            return Optional.empty();
        }

        return Optional.of(toSave);
    }
}
