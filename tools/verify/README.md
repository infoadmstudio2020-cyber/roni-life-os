# Verification harness

Compile-free checks for the Native API / WebBridge layer. Run from this directory.

```bash
npm i jsdom                 # only needed for the two .mjs DOM tests
python3 extract_actions.py  # action inventory + registration cross-check -> actions.json
python3 storage_scope_test.py
python3 java_sanity.py
node wrapper_test.mjs
node examples_runtime_test.mjs
node smoke.mjs
python3 gen_matrix.py       # regenerate docs/bangla/API_REGISTRATION_MATRIX.md
```

`extract_actions.py` and `gen_matrix.py` expect the repo at
`/workspace/app-fu3r65ukcnwh/tasks/extracted/RONiLiFEOS`; edit `ROOT` if you move it.

No `javac` is required. No `javac` is available in the reference environment either, so the
Java sources are **not** compiled by these scripts — see `docs/bangla/TESTING_AND_VERIFICATION.md` §3.
