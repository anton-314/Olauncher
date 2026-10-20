package app.olauncher.helper

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import org.json.JSONArray
import java.io.File

/**
 * Due cards from AnkiDroid's content provider, for the breathing pause. The column names and
 * URIs follow FlashCardsContract in AnkiDroid's api module, used without the library.
 * All calls do IO and belong off the main thread.
 */
object AnkiDroid {

    const val PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    private const val AUTHORITY = "com.ichi2.anki.flashcards"
    private val AUTHORITY_URI = "content://$AUTHORITY".toUri()
    private val SCHEDULE_URI = Uri.withAppendedPath(AUTHORITY_URI, "schedule")
    private val DECKS_URI = Uri.withAppendedPath(AUTHORITY_URI, "decks")
    private val NOTES_URI = Uri.withAppendedPath(AUTHORITY_URI, "notes")
    private val MODELS_URI = Uri.withAppendedPath(AUTHORITY_URI, "models")

    // The provider gives no read access to media, so images are read from the AnkiDroid folder.
    // This is the folder in shared storage; an app-specific folder under Android/data is unreadable.
    val mediaDirectory: File
        get() = File(Environment.getExternalStorageDirectory(), "AnkiDroid/collection.media")

    data class Deck(val id: Long, val name: String)

    /** A queued card; [nextReviewTimes] holds the interval label for each of the four answers. */
    data class DueCard(val noteId: Long, val ord: Int, val nextReviewTimes: List<String>)

    data class CardContent(val question: String, val answer: String, val css: String)

    fun isInstalled(context: Context): Boolean {
        val providerInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.packageManager.resolveContentProvider(AUTHORITY, PackageManager.ComponentInfoFlags.of(0))
        else
            @Suppress("DEPRECATION")
            context.packageManager.resolveContentProvider(AUTHORITY, 0)
        return providerInfo != null
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun canReadMedia(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED

    fun getDecks(context: Context): List<Deck> = try {
        context.contentResolver.query(DECKS_URI, arrayOf("deck_id", "deck_name"), null, null, null)?.use {
            buildList {
                while (it.moveToNext()) add(Deck(it.getLong(0), it.getString(1)))
            }.sortedBy { deck -> deck.name.lowercase() }
        } ?: emptyList()
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }

    // The provider selects the deck for the query only and selects the previous one again afterwards
    fun getDueCards(context: Context, deckId: Long, limit: Int): List<DueCard> = try {
        context.contentResolver.query(
            SCHEDULE_URI,
            arrayOf("note_id", "ord", "next_review_times"),
            "limit=?,deckID=?",
            arrayOf(limit.toString(), deckId.toString()),
            null
        )?.use {
            buildList {
                while (it.moveToNext()) {
                    val times = JSONArray(it.getString(2))
                    add(DueCard(it.getLong(0), it.getInt(1), List(times.length()) { i -> times.getString(i) }))
                }
            }
        } ?: emptyList()
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }

    // The rendered question and answer come without the note type's styling, which is fetched separately
    fun getCardContent(context: Context, card: DueCard): CardContent? = try {
        val resolver = context.contentResolver
        val noteUri = Uri.withAppendedPath(NOTES_URI, card.noteId.toString())
        val cardUri = Uri.withAppendedPath(noteUri, "cards/${card.ord}")
        val (question, answer) = resolver.query(cardUri, arrayOf("question", "answer"), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) to it.getString(1) else null
        } ?: return null
        val modelId = resolver.query(noteUri, arrayOf("mid"), null, null, null)?.use {
            if (it.moveToFirst()) it.getLong(0) else null
        }
        val css = modelId?.let { mid ->
            resolver.query(Uri.withAppendedPath(MODELS_URI, mid.toString()), arrayOf("css"), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }
        CardContent(question.orEmpty(), answer.orEmpty(), css.orEmpty())
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    /** [ease] is 1 (again) to 4 (easy), as on AnkiDroid's answer buttons. */
    fun answer(context: Context, card: DueCard, ease: Int, timeTakenMs: Long): Boolean = try {
        val values = ContentValues().apply {
            put("note_id", card.noteId)
            put("ord", card.ord)
            put("answer_ease", ease)
            put("time_taken", timeTakenMs)
        }
        context.contentResolver.update(SCHEDULE_URI, values, null, null) > 0
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
