package micromod.compiler;

public class FineTune implements Element {
	private Instrument parent;
	private Waveform sibling;

	public FineTune( Instrument parent ) {
		this.parent = parent;
		sibling = new Waveform( parent );
	}
	
	public String getToken() {
		return "FineTune";
	}
	
	public Element getParent() {
		return parent;
	}
	
	public Element getSibling() {
		return sibling;
	}
	
	public Element getChild() {
		return null;
	}
	
	public void begin( String value ) {
		parent.setFineTune( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}
}
