"""docs/workflows/docs-notes.md sends a reader to docs/devices/ and never said the note has to be listed.

The page is where a troubleshooting entry, a `PROMPT.md` sentence and brand-specific knowledge go, and it
says "brand-specific knowledge goes in `examples/` or `docs/devices/`" - so a note written the way it reads
lands in docs/devices/ and stops there. tests/test_device_notes_index.py then fails the whole suite: on
eb0131a3 a note added exactly that way gives

    AssertionError: Lists differ: [] != ['docs/devices/test-brand-tb100.md']

which is the right failure (a note nothing links to is only found by browsing the folder on GitHub) and the
wrong step to discover it at, since the page's own last line is "Then run the tests". Three merged fixes
already closed this shape in other recipes - the showcase count (#1348) and the switches page's limits
(#1388) - and this is the same one: the recipe states every requirement the checker enforces.

The numbers and the heading are not copied in here. NOTE_INDEX_HEADING and the three checkers come from
tests/test_device_notes_index.py, so a page rewritten around a renamed heading fails rather than passing on
yesterday's wording, and the checkers are run over a tree written here to prove the two destinations the
page now names are the ones that silence them.
"""

import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))  # noqa: E402

from test_device_notes_index import (  # noqa: E402
    NOTE_INDEX_HEADING,
    index_notes,
    index_notes_with_their_own_list,
    rows_not_linking_their_note,
    unlinked_notes,
)

PAGE = ROOT / "docs" / "workflows" / "docs-notes.md"
# The file that fails, named in the page so a reader knows which check they are answering.
CHECKER = "tests/test_device_notes_index.py"
# A recipe has at least the troubleshooting bullet, the PROMPT.md bullet and the README bullet.
MIN_BULLETS = 3


def bullets(text):
    """The page's bullets, each joined from its wrapped continuation lines.

    A fenced block is an example a reader copies, not a requirement, so it is skipped whole; a line that is
    neither indented nor a new bullet ends the bullet above it, the way Markdown reads them.
    """
    out, current, fenced = [], [], False
    for line in text.splitlines():
        if line.strip().startswith("```"):
            fenced = not fenced
            continue
        if fenced:
            continue
        if line.startswith("- "):
            if current:
                out.append(" ".join(current))
            current = [line[2:].strip()]
        elif current and line[:1] in (" ", "\t") and line.strip():
            current.append(line.strip())
        elif current:
            out.append(" ".join(current))
            current = []
    if current:
        out.append(" ".join(current))
    return out


def note_bullets(text):
    """The bullets that talk about a note in docs/devices/ - the ones a note author reads."""
    return [bullet for bullet in bullets(text) if "docs/devices/" in bullet]


def test_page_text():
    return PAGE.read_text(encoding="utf-8")


class RecipeStatesTheRequirementTest(unittest.TestCase):
    def test_the_page_has_the_bullets_to_read(self):
        self.assertGreaterEqual(len(bullets(test_page_text())), MIN_BULLETS, "the page's bullets were not read")

    def test_one_bullet_asks_for_the_index_line_and_names_the_check(self):
        text = test_page_text()
        found = [
            bullet for bullet in note_bullets(text)
            if NOTE_INDEX_HEADING in bullet and "examples/README.md" in bullet and CHECKER in bullet
        ]
        self.assertEqual(1, len(found),
                         "no bullet sends a new docs/devices/ note to examples/README.md and names "
                         f"{CHECKER}:\n" + "\n".join(note_bullets(text)))

    def test_the_bullet_states_both_destinations(self):
        # A note under docs/devices/ goes one of two ways, and the checker holds both: the row of its own
        # list (rows_not_linking_their_note) or the line under the no-list heading.
        text = test_page_text()
        found = [bullet for bullet in note_bullets(text) if "examples/<model>/" in bullet]
        self.assertEqual(1, len(found),
                         "no bullet says a note with a list of its own is linked from that folder's row:\n"
                         + "\n".join(note_bullets(text)))

    def test_the_heading_the_page_quotes_is_the_one_the_checker_uses(self):
        # Not the page's own words: the constant the checker matches, so a renamed heading in the checker
        # and a page left behind cannot agree by accident.
        self.assertIn(NOTE_INDEX_HEADING, test_page_text())

    def test_a_mention_in_prose_is_not_the_requirement(self):
        # The page ends with "Then run the tests: ..." and names paths in prose too; a requirement has to be
        # a bullet a reader follows, so the trailing paragraph alone never counts.
        prose_only = (
            f"Then run {CHECKER} and link the note from examples/README.md, {NOTE_INDEX_HEADING}.\n"
        )
        self.assertEqual([], bullets(prose_only))

    def test_a_fenced_example_is_not_read_as_a_bullet(self):
        page = (
            "- Real bullet.\n\n"
            "```\n"
            f"- A note goes in examples/README.md under {NOTE_INDEX_HEADING}, see {CHECKER}.\n"
            "```\n"
        )
        self.assertEqual(["Real bullet."], bullets(page))

    def test_a_wrapped_bullet_is_read_whole(self):
        page = (
            "- A note in `docs/devices/` is linked from\n"
            "  `examples/README.md`, and `tests/test_device_notes_index.py`\n"
            "  fails until it is.\n"
            "- Another bullet.\n"
        )
        self.assertEqual(
            ["A note in `docs/devices/` is linked from `examples/README.md`, and "
             "`tests/test_device_notes_index.py` fails until it is.",
             "Another bullet."],
            bullets(page))


class TheRecipeWorksTest(unittest.TestCase):
    """Run the real checkers over a tree written the way the page now says to write it."""

    @staticmethod
    def _tree(tmp, index_line=True, folder=False):
        root = Path(tmp)
        (root / "docs" / "devices").mkdir(parents=True)
        (root / "docs" / "devices" / "new-brand-tb100.md").write_text(
            "# New brand TB-100\n\nA one-day report; no list of its own yet.\n", encoding="utf-8")
        (root / "examples").mkdir()
        page = f"# Lists\n\n{NOTE_INDEX_HEADING} live in [`docs/devices/`](../docs/devices/):\n\n"
        if folder:
            directory = root / "examples" / "new-brand-tb100"
            directory.mkdir()
            (directory / "debloat.txt").write_text("# a list\n", encoding="utf-8")
            page = ("# Lists\n\n| Folder | TV | OS | Notes |\n|---|---|---|---|\n"
                    "| `new-brand-tb100/` | New brand TB-100 | Android 11 | "
                    "one-day list; [note](../docs/devices/new-brand-tb100.md) |\n\n"
                    f"## {NOTE_INDEX_HEADING}\n\n")
        elif index_line:
            page += "- [New brand TB-100](../docs/devices/new-brand-tb100.md): a one-day report.\n"
        (root / "examples" / "README.md").write_text(page, encoding="utf-8")
        return root

    def test_a_note_in_the_index_is_reachable(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self._tree(tmp)
            self.assertEqual([], unlinked_notes(root))
            self.assertEqual(["new-brand-tb100.md"], index_notes(root))
            self.assertEqual([], index_notes_with_their_own_list(root))

    def test_a_note_linked_from_its_own_list_row_is_reachable(self):
        # The other destination the bullet names: the folder exists, so the row carries the link.
        with tempfile.TemporaryDirectory() as tmp:
            root = self._tree(tmp, folder=True)
            self.assertEqual([], unlinked_notes(root))
            self.assertEqual([], rows_not_linking_their_note(root))

    def test_a_note_the_recipe_skips_is_the_suite_failure(self):
        # This is the check that failed on eb0131a3 when the page was followed on its own: pinned here so
        # the failure the new bullet prevents does not quietly stop being a failure.
        with tempfile.TemporaryDirectory() as tmp:
            root = self._tree(tmp, index_line=False)
            self.assertEqual(["docs/devices/new-brand-tb100.md"], unlinked_notes(root))

    def test_a_note_filed_under_the_heading_but_holding_its_own_list_is_reported(self):
        # The heading's other half, which the bullet states: once the list exists the entry belongs to the
        # row, so a report left in the index contradicts what the repo ships.
        with tempfile.TemporaryDirectory() as tmp:
            root = self._tree(tmp)
            note = root / "docs" / "devices" / "new-brand-tb100.md"
            note.write_text(
                "# New brand TB-100\n\nA tested package list for this unit lives in "
                "[`examples/new-brand-tb100/debloat.txt`](../../examples/new-brand-tb100/debloat.txt).\n",
                encoding="utf-8")
            directory = root / "examples" / "new-brand-tb100"
            directory.mkdir()
            (directory / "debloat.txt").write_text("# a list\n", encoding="utf-8")
            offenders = index_notes_with_their_own_list(root)
            self.assertEqual(1, len(offenders))
            self.assertIn("examples/new-brand-tb100/debloat.txt", offenders[0])


if __name__ == "__main__":
    unittest.main()
