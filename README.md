# ![chrondb](https://user-images.githubusercontent.com/31996/161378505-4a8824b6-d5f1-4ce0-897b-cec1c5820b26.png)

> ⚠️ this project is in development ⚠️

_Chronological **key/value** Database storing based on database-shaped `git` (core) architecture_

**we are:**
- Immutable and atomic data
- ACID transactions
- Schemaless
- Chronological
- SQL compliance - in query within document

Understand how and when changes were made. **chrondb** stores all history, and lets you query against any point in time.

Git structure is a powerful solution for storing **"data"** (files) in chronological order, _chrondb_ uses git core as a data structure to structure the data timeline, making it possible to return to any necessary point and bringing all git functions for a database:

- diff
- notes
- restore
- branch
- checkout
- revert
- merge
- log
- blame
- archive
- [hooks](https://git-scm.com/docs/githooks#_hooks)
- ... [git high-level commands (porcelain)](https://git-scm.com/docs/git#_high_level_commands_porcelain)

## Term alignment, from database to git

> The goal is to speak the same language as the database world

- database: _git_ repository (local or remotely)
- scheme: _git_ branch
- table: directory added on _git_ repository
- field struct: json (document) - will be persisted in a file and indexed in _lucene_

## Features

- **Historic change** with chronological evolution - _aka_ git commits (gpg signature support)
- Document - schemeless
- Gzip compress content - when there's need for a very fast compression, gzip is the clear winner, [benchmark](https://tukaani.org/lzma/benchmarks.html)
- Search and Full Text Search _(by lucene)_
- Sorting by any field
- Flexible faceting, highlighting, joins and result grouping
- Cross-Platform Solution - Linux, \*BSD, macOS and Windows
- Transactions - git merge after temp branch save
- Cluster - multi git repository (regardless of location)
- High availability
- Plugable/Expandable (possibility of integration with tools that connects in git repository, e.g. jenkins)
- Triggers by events via hooks (pre-receive, update and post-receive)
  - Data replication - _post-receive_

## Communication Protocol
- Clojure package
- _ODBC_/**JDBC** - SQL Compliance
- RESTful and/or GraphQL

## Problems

- Large volume of writing, possible solution **[git lfs](https://git-lfs.github.com/)**, [jgit implementation](https://github.com/eclipse/jgit/blob/master/org.eclipse.jgit.lfs/src/org/eclipse/jgit/lfs/Lfs.java);

## RUN

```sh
clj -X:run
```

## Test

```sh
clj -X:test
```
