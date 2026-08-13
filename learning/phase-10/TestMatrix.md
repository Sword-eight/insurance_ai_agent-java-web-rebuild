# Phase 10 Test Matrix

| Boundary | Normal path | Abnormal path |
|---|---|---|
| Public HTTP | authenticated PDF upload, list and owned detail | missing auth, invalid PDF, 413/415, foreign document |
| Java storage | UUID storage key, digest and controlled load | path-like name, MIME/signature mismatch, size limit, metadata compensation |
| Java orchestration | `UPLOADED -> INDEXING -> INDEXED` outside long transaction | explicit failure -> `FAILED`; timeout/delivery unknown -> `UNKNOWN` |
| Java -> Python | multipart metadata + binary file + TraceId | internal rejection, malformed envelope, timeout, mismatched IDs |
| Python Facade | size/signature/SHA validation and rebuild | invalid document, candidate build failure, temporary-file cleanup |
| FAISS lifecycle | candidate generation then atomic marker publication | failed candidate preserves loaded/active index; rebuilds serialize |
| MySQL 8 | empty schema migrates through V2 | existing Phase 7 V1 schema upgrades to V2 without losing rows |
| Regression | Java full test suite and Python full pytest | zero unexplained failures or skips |

The final audit records actual collected/pass/fail/skip counts; this matrix is not execution evidence.
