package micromod.compiler;

public class Pitch implements Element {
	private WaveFile parent;

	public Pitch( WaveFile parent ) {
		this.parent = parent;
	}
	
	public String getToken() {
		return "Pitch";
	}
	
	public Element getParent() {
		return parent;
	}
	
	public Element getSibling() {
		return null;
	}
	
	public Element getChild() {
		return null;
	}
	
	public void begin( String value ) {
		parent.setPitch( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}

	public String description() {
		return "\"0\" (Tune the sample in eighths of a semitone.)";
	}
}
