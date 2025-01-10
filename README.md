# ![chrondb](https://user-images.githubusercontent.com/31996/161378505-4a8824b6-d5f1-4ce0-897b-cec1c5820b26.png)

> ⚠️ this project is in development ⚠️

_Chronological **key/value** Database storing based on database-shaped `git` (core) architecture_

## Features

- **Historic change** with chronological evolution - _aka_ git commits (gpg signature support)
- Document - schemeless
- Gzip compress content
- Search and Full Text Search _(by lucene)_
- Sorting by any field
- Flexible faceting, highlighting, joins and result grouping
- Cross-Platform Solution - Linux, \*BSD, macOS and Windows
- Transactions - git merge after temp branch save
- Cluster - multi git repository (regardless of location)
- High availability
- Plugable/Expandable
- Triggers by events via hooks (pre-receive, update and post-receive)
  - Data replication - _post-receive_

## Architecture

The project is organized in a modular way, following functional programming principles:

```
src/chrondb/
├── core/           # Core Git operations
├── storage/        # Storage implementations
├── index/          # Search index implementations
├── compression/    # Compression utilities
└── api/            # API implementations
```

### Components

- **Storage**: Handles data persistence using Git as the storage engine
- **Index**: Manages search indexes using Lucene
- **API**: Provides REST endpoints for data operations

### Communication

Components communicate through well-defined protocols:

```clojure
;; Storage Protocol
(defprotocol Storage
  (save [this key value])
  (get-value [this key])
  (delete [this key]))

;; Index Protocol
(defprotocol Index
  (index! [this document fields])
  (search [this query limit])
  (remove! [this document]))
```

## API

### REST Endpoints

- `GET /api/v1/get/:key` - Retrieve a value by key
- `POST /api/v1/save` - Save a value with optional indexing
- `DELETE /api/v1/delete/:key` - Delete a value
- `GET /api/v1/search` - Search indexed documents

### Clojure API

```clojure
(require '[chrondb.core :as chrondb])

;; Create a new ChronDB instance
(def db (chrondb/create-chrondb))

;; Save a value
(chrondb/save db "user:1" {:name "John" :age 30} :fields [:name :age])

;; Get a value
(chrondb/get-value db "user:1")

;; Search
(chrondb/search db {:name "John"} :limit 10)

;; Delete
(chrondb/delete db "user:1")
```

## Development

### Prerequisites

- Java 11+
- Clojure 1.11.1+

### Running

```sh
clj -X:run
```

### Testing

```sh
clj -X:test
```

### Development REPL

```sh
clj -M:dev
```

## License

Copyright © 2024 Avelino

Distributed under the Eclipse Public License version 1.0.
