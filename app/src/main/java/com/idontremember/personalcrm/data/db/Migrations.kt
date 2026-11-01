package com.idontremember.personalcrm.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `contact_tags` (`contact_id` INTEGER NOT NULL, `tag_id` INTEGER NOT NULL, PRIMARY KEY(`contact_id`, `tag_id`), FOREIGN KEY(`contact_id`) REFERENCES `contacts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`tag_id`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_tags_contact_id` ON `contact_tags` (`contact_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_tags_tag_id` ON `contact_tags` (`tag_id`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `relationships` (`contact_a` INTEGER NOT NULL, `contact_b` INTEGER NOT NULL, `label` TEXT NOT NULL, PRIMARY KEY(`contact_a`, `contact_b`, `label`), FOREIGN KEY(`contact_a`) REFERENCES `contacts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`contact_b`) REFERENCES `contacts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_relationships_contact_a` ON `relationships` (`contact_a`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_relationships_contact_b` ON `relationships` (`contact_b`)")
    }
}
