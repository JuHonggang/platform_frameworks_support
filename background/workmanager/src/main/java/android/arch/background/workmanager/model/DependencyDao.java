/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.arch.background.workmanager.model;

import static android.arch.persistence.room.OnConflictStrategy.FAIL;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.List;

/**
 * The Data Access Object for {@link Dependency}.
 */
@Dao
public interface DependencyDao {
    /**
     * Attempts to insert a {@link Dependency} into the database.
     *
     * @param dependency The {@link Dependency}s to insert
     */
    @Insert(onConflict = FAIL)
    void insertDependency(Dependency dependency);

    /**
     * Gets all {@link Dependency} rows in the table.
     *
     * @return All {@link Dependency} items in the table.
     */
    @Query("SELECT * FROM dependency")
    List<Dependency> getAllDependencies();

    /**
     * Delete all {@link Dependency} with {@code prerequisiteId}.
     * @param prerequisiteId ID of a {@link WorkSpec} that is a prerequisite for another one.
     */
    @Query("DELETE FROM dependency WHERE prerequisite_id=:prerequisiteId")
    void deleteDependenciesWithPrerequisite(String prerequisiteId);

    /**
     * Determines if a {@link WorkSpec is dependent on other {@link WorkSpec}s.
     *
     * @param id The identifier for the {@link WorkSpec}
     * @return {@code true} if the {@link WorkSpec} is dependent on other {@link WorkSpec}s
     */
    @Query("SELECT COUNT(*) > 0 FROM dependency WHERE work_spec_id=:id")
    boolean hasPrerequisites(String id);

    /**
     * Gets all the direct prerequisites for a particular {@link WorkSpec}.
     *
     * @param id The {@link WorkSpec} identifier
     * @return A list of all prerequisites for {@code id}
     */
    @Query("SELECT prerequisite_id FROM dependency WHERE work_spec_id=:id")
    List<String> getPrerequisites(String id);
}
