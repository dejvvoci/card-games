package com.pesekatesh.stats;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Hibernate (spring.jpa.hibernate.ddl-auto=update) s'e rifreskon vetë CHECK constraint-in e kolonës
 * game_type kur shtohen vlera të reja në enum GameType (p.sh. DERR) — e heqim këtu manualisht, në
 * mënyrë të sigurt dhe idempotente, çdo herë që aplikacioni niset, që lojëra të reja të mos bllokohen
 * nga një constraint i vjetëruar i krijuar për enumin e vjetër.
 */
@Component
public class GameTypeConstraintFix {

    private final DataSource dataSource;

    public GameTypeConstraintFix(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void dropStaleCheckConstraint() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE game_result DROP CONSTRAINT IF EXISTS game_result_game_type_check");
        } catch (SQLException ex) {
            // Tabela mund të mos ekzistojë ende në ekzekutimin krejt të parë -> injorohet,
            // Hibernate e krijon menjëherë pas këtij hapi.
        }
    }
}
