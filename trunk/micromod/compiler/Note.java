
package micromod.compiler;

public class Note implements Element {
	private Macro parent;
	private Porta child = new Porta( this );
	private micromod.Note note = new micromod.Note();
	private int fade, repeat, porta, timeStretch;

	public Note( Macro parent ) {
		this.parent = parent;
	}
	
	public String getToken() {
		return "Note";
	}
	
	public Element getParent() {
		return parent;
	}
	
	public Element getSibling() {
		return null;
	}
	
	public Element getChild() {
		return child;
	}
	
	public void begin( String value ) {
		fade = repeat = porta = timeStretch = 0;
		note.fromString( value );
		if( note.effect == 0xC && note.parameter > 0x40 ) {
			/* Apply x^2 volume-curve for effect C41 to C4F. */
			int vol = ( note.parameter & 0xF ) + 1;
			note.parameter = ( vol * vol ) >> 2;
		}
	}

	public void end() {
		parent.nextNote( note, fade, repeat, porta, timeStretch );
	}

	public void setPorta( int semitones ) {
		porta = semitones;
	}

	public void setTimeStretch( int rows ) {
		timeStretch = rows;
	}

	public void beginEffect( String token ) {
		if( "Repeat".equals( token ) ) {
			repeat = Macro.BEGIN;
		} else if( "Fade".equals( token ) ) {
			fade = Macro.BEGIN;
		} else {
			throw new IllegalArgumentException( "Unknown command: Begin " + token );
		}
	}

	public void endEffect( String token ) {
		if( "Fade".equals( token ) ) {
			fade = Macro.END;
		} else {
			throw new IllegalArgumentException( "Unknown command: End " + token );
		}
	}

	public void endRepeat( int count ) {
		if( count < 2 ) {
			throw new IllegalArgumentException( "Invalid repeat count (2 or more): " + count );
		}
		repeat = count;
	}
}
