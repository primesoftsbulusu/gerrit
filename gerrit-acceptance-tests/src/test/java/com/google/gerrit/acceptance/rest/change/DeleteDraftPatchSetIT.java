// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeStatus;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwtorm.server.OrmException;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Test;

import java.io.IOException;

public class DeleteDraftPatchSetIT extends AbstractDaemonTest {

  @Test
  public void deletePatchSet() throws Exception {
    String changeId = createChange().getChangeId();
    PatchSet ps = getCurrentPatchSet(changeId);
    String triplet = "p~master~" + changeId;
    ChangeInfo c = get(triplet);
    assertThat(c.id).isEqualTo(triplet);
    assertThat(c.status).isEqualTo(ChangeStatus.NEW);
    RestResponse r = deletePatchSet(changeId, ps, adminSession);
    assertThat(r.getEntityContent()).isEqualTo("Patch set is not a draft.");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CONFLICT);
  }

  @Test
  public void deleteDraftPatchSetNoACL() throws Exception {
    String changeId = createDraftChangeWith2PS();
    PatchSet ps = getCurrentPatchSet(changeId);
    String triplet = "p~master~" + changeId;
    ChangeInfo c = get(triplet);
    assertThat(c.id).isEqualTo(triplet);
    assertThat(c.status).isEqualTo(ChangeStatus.DRAFT);
    RestResponse r = deletePatchSet(changeId, ps, userSession);
    assertThat(r.getEntityContent()).isEqualTo("Not found: " + changeId);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void deleteDraftPatchSetAndChange() throws Exception {
    String changeId = createDraftChangeWith2PS();
    PatchSet ps = getCurrentPatchSet(changeId);
    String triplet = "p~master~" + changeId;
    ChangeInfo c = get(triplet);
    assertThat(c.id).isEqualTo(triplet);
    assertThat(c.status).isEqualTo(ChangeStatus.DRAFT);
    RestResponse r = deletePatchSet(changeId, ps, adminSession);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_NO_CONTENT);
    Change change = Iterables.getOnlyElement(db.changes().byKey(
        new Change.Key(changeId)).toList());
    assertThat(db.patchSets().byChange(change.getId()).toList()).hasSize(1);
    ps = getCurrentPatchSet(changeId);
    r = deletePatchSet(changeId, ps, adminSession);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_NO_CONTENT);
    assertThat(db.changes().byKey(new Change.Key(changeId)).toList()).isEmpty();
  }

  private String createDraftChangeWith2PS() throws GitAPIException,
      IOException {
    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    Result result = push.to(git, "refs/drafts/master");
    push = pushFactory.create(db, admin.getIdent(), PushOneCommit.SUBJECT,
        "b.txt", "4711", result.getChangeId());
    return push.to(git, "refs/drafts/master").getChangeId();
  }

  private PatchSet getCurrentPatchSet(String changeId) throws OrmException {
    return db.patchSets()
        .get(Iterables.getOnlyElement(db.changes()
            .byKey(new Change.Key(changeId)))
            .currentPatchSetId());
  }

  private static RestResponse deletePatchSet(String changeId,
      PatchSet ps, RestSession s) throws IOException {
    return s.delete("/changes/"
        + changeId
        + "/revisions/"
        + ps.getRevision().get());
  }
}
