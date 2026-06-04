# Reader Tool Design

## Goal

Add a local reader tool to the toolbox app for reading novels and comics from user-selected files and folders.

The first version is local-only. It does not search online sources, scrape websites, sync to cloud storage, or download content. The user must explicitly choose files or folders through Android system file pickers.

## Supported Formats

First-version supported formats:

- Novels: `.txt`, `.epub`, `.pdf`
- Comics: image folders, `.zip`, `.cbz`
- Images inside comic folders or archives: `.jpg`, `.jpeg`, `.png`, `.webp`, `.gif`

The parser architecture should allow later support for `.mobi`, `.azw3`, `.rar`, `.cbr`, and `.7z`, but those formats are not first-version requirements.

Encrypted EPUB/PDF files are not supported in the first version. If a file cannot be opened or parsed, the UI should show a clear error and allow the user to remove the item from the bookshelf.

## User Experience

### Toolbox Entry

The toolbox home adds a `Reader` tool card. The card opens the reader bookshelf. Long-press favorite behavior should match the existing backup, movie, and music tool cards.

### Bookshelf

The bookshelf is the first reader screen. It shows locally added books and comics with:

- Title
- Content type: novel, comic, or PDF
- Source display path or file name
- Last reading progress
- Last opened time
- Open action
- Remove action

Empty bookshelf state should offer two actions:

- Add file
- Add folder

### Adding Content

The reader supports two add flows:

- Add a single file with Android `OpenDocument`
- Add a folder with Android `OpenDocumentTree`, then scan supported files in that folder

The app stores persistable SAF permissions for selected files/folders when Android grants them. It should store URI strings rather than pretending they are filesystem paths.

Folder scanning should ignore unsupported files and add supported files as bookshelf items. A folder containing images directly can be added as one comic. A folder containing multiple supported book/archive files can add multiple bookshelf items.

### Novel Reading

TXT and EPUB reading uses a text reader screen with:

- Scroll-based reading in the first version
- Font size controls
- Light/dark/sepia-style background choices
- Progress persistence
- System back returns to the bookshelf

TXT parsing should handle UTF-8 and common Chinese encodings where feasible. If encoding detection is uncertain, prefer a readable fallback and show a clear error only when text cannot be decoded.

EPUB parsing should extract readable spine documents in order. It does not need full CSS fidelity in the first version.

### PDF Reading

PDF reading uses Android `PdfRenderer` where possible:

- Page-by-page rendering
- Current page persistence
- Basic zoom or fit-to-width behavior

PDF text extraction, annotations, and encrypted PDFs are out of scope for the first version.

### Comic Reading

Comic reading supports:

- Image folders
- `.zip` and `.cbz` archives containing supported image files
- Natural filename sorting
- Vertical continuous reading as the default
- Progress persistence by image index
- Pinch zoom if feasible with existing Compose/Android primitives

Archive contents should be read safely. The implementation must avoid path traversal when handling zip entries.

## Architecture

### Data Model

Create a reader bookshelf item model with:

- Stable ID
- Title
- Type: text, epub, pdf, comic folder, comic archive
- Source URI
- Optional parent folder URI
- Display path/name
- Last progress value
- Last opened timestamp
- Optional metadata such as page count or chapter count

Persist bookshelf items and progress in a local store. Follow the existing local store pattern used by `FavoriteStore`, `MusicStore`, and `MovieSourceStore`.

### Services

Reader logic should be split into focused services:

- `ReaderLibraryStore`: persists bookshelf items, progress, and reader settings
- `ReaderImportService`: classifies selected files/folders and creates bookshelf items
- `TextReaderService`: loads TXT content
- `EpubReaderService`: loads EPUB spine text
- `PdfReaderService`: opens and renders PDF pages
- `ComicReaderService`: lists images from folders or zip/cbz archives

Each service should expose small interfaces that can be unit tested without Compose.

### ViewModels

Use a dedicated `ReaderViewModel` for:

- Bookshelf state
- Add file/folder results
- Remove item
- Open item routing
- Progress updates
- Reader settings

Avoid adding reader parsing logic directly to `MainActivity` or Compose screens.

### Screens

Create reader-specific screens:

- `ReaderBookshelfScreen`
- `TextReaderScreen`
- `PdfReaderScreen`
- `ComicReaderScreen`

`MainActivity` owns Activity Result launchers for file/folder selection and routes selected URIs into the ViewModel, matching the existing music folder picker pattern.

## Error Handling

Reader errors should be user-facing and recoverable:

- Unsupported format: explain that the file type is not supported
- Missing permission: ask the user to reselect the file/folder
- Parse failure: show the file name and a short reason
- Empty folder: explain that no supported novels or comics were found
- Corrupt archive/PDF/EPUB: show a clear failure and keep the bookshelf usable

No parsing failure should crash the app.

## Testing

Unit tests should cover:

- File extension classification
- Natural sorting for comic image names
- Zip entry filtering and path traversal rejection
- Bookshelf item persistence serialization
- TXT decoding fallback behavior where practical

Build verification:

```powershell
.\gradlew.bat assembleDebug
```

Manual device checks should cover:

- Add a TXT file
- Add an EPUB file
- Add a PDF file
- Add a folder of comic images
- Add a CBZ/ZIP comic
- Rotate while reading and confirm the reader stays on the current item
- Reopen the app and confirm progress persists
