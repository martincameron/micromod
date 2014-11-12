package micromod.compiler;

public class Chorus implements Element {
	private Waveform parent;
	private Point sibling;
	private Type child = new Type( this );
	private int chorus;
	private boolean type;

	public Chorus( Waveform parent ) {
		this.parent = parent;
		sibling = new Point( parent );
	}
	
	public String getToken() {
		return "Chorus";
	}
	
	public Element getParent() {
		return parent;
	}
	
	public Element getSibling() {
		return sibling;
	}
	
	public Element getChild() {
		return child;
	}
	
	public void begin( String value ) {
		chorus = Parser.parseInteger( value );
		type = false;
	}
	
	public void end() {
		parent.setChorus( chorus, type );
	}

	public void setChorusType( boolean pwm ) {
		this.type = pwm;
	}
}
