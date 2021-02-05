# Conclave External Docs

To build the static docsite, run `./make-docsite.sh`. It will be built in the `build` directory.

To render the docsite for development, use `mkdocs serve`:
```
# Build the site first. This will install mkdocs in a virtualenv which we can reuse
./make-docsite.sh

# Activate the virtualenv
source virtualenv/bin/activate

# Serve the docs at localhost:8000. Edits to the files will be reflected immediately.
mkdocs serve
```
