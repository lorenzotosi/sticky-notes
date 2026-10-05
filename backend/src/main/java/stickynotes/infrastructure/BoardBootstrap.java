// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.infrastructure;

import com.mongodb.client.result.UpdateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import stickynotes.interop.JvmBoardFacade;

/**
 * Initializes the {@code main} board during Spring Boot startup without resetting existing data.
 *
 * <p>Initialization is separate from ordinary repository saves and uses an insert-only upsert.
 */
@Component
public class BoardBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BoardBootstrap.class);

    private final MongoTemplate mongoTemplate;

    /**
     * Creates the startup initializer using the application's MongoDB template.
     *
     * @param mongoTemplate the template used for the insert-only board upsert
     */
    public BoardBootstrap(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Ensures that the board exists using the defaults from {@link JvmBoardFacade#createEmptyBoard()}.
     *
     * <p>The query matches only {@code _id=main}; all initial fields use {@code $setOnInsert}, so an
     * existing board retains its data and revision. Duplicate keys from concurrent startup are
     * tolerated. Database resource failures are logged and allow startup to continue; restarting
     * the backend after database recovery is required to retry initialization.
     *
     * @param args the application startup arguments; not used for board initialization
     * @throws IllegalStateException if MongoDB does not acknowledge initialization
     * @throws org.springframework.dao.DataAccessException for database errors other than duplicate
     *     keys or resource failures
     */
    @Override
    public void run(ApplicationArguments args) {
        BoardDocument initial = BoardDocumentMapper.toDocument(JvmBoardFacade.createEmptyBoard());
        Query query = Query.query(Criteria.where("_id").is(initial.id()));
        Update update = new Update()
                .setOnInsert("schemaVersion", initial.schemaVersion())
                .setOnInsert("revision", initial.revision())
                .setOnInsert("wipLimit", initial.wipLimit())
                .setOnInsert("notes", initial.notes());

        try {
            UpdateResult result = mongoTemplate.upsert(query, update, BoardDocument.class);
            if (!result.wasAcknowledged()) {
                throw new IllegalStateException("MongoDB did not acknowledge the board initialization");
            }
        } catch (DuplicateKeyException alreadyCreated) {
            // Another startup created main; preserve the existing board.
        } catch (DataAccessResourceFailureException unavailable) {
            LOGGER.warn("Board initialization unavailable; restart the backend after MongoDB recovery");
        }
    }
}
