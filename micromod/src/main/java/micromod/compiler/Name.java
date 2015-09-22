package micromod.compiler;

public class Name implements Element {
	private Instrument parent;
	private Volume sibling;

	public Name( Instrument parent ) {
		this.parent = parent;
		sibling = new Volume( parent );
	}
	
	public String getToken() {
		return "Name";
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
		parent.setName( value );
	}
	
	public void end() {
	}

	public String description() {
		return "\"Name\" (Instrument name, maximum 22 characters.)";
	}
}
