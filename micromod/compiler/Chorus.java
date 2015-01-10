package micromod.compiler;

public class Chorus implements Element {
	private Waveform parent;
	private Point sibling;
	private int cycles, modRate, lfoRate, detune, mix;

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
		return null;
	}
	
	public void begin( String value ) {
		int[] values = Parser.parseIntegerArray( value );
		if( values.length < 1 || values.length > 5 ) {
			throw new IllegalArgumentException( "Invalid Chorus parameter (Cycles[,ModRate,LfoRate,Detune,Mix]): " + value );
		}
		cycles = values[ 0 ];
		modRate = values.length > 1 ? values[ 1 ] : 1;
		lfoRate = values.length > 2 ? values[ 2 ] : 0;	
		detune = values.length > 3 ? values[ 3 ] : 0;
		mix = values.length > 4 ? values[ 4 ] : 128;
	}
	
	public void end() {
		parent.setModulation( cycles, modRate, lfoRate, detune, mix );
	}

	public String description() {
		return "\"Cycles[,ModRate,LfoRate,Detune,Mix]\" (Phase modulation.)\n" +
			"(Cycles controls cycles of unmodulated oscillator 1.)\n" +
			"(ModRate controls cycles of osc 2 modulation. Default 1.)\n" +
			"(LfoRate controls cycles of modulator LFO. Default 0.)\n" +
			"(Detune adjusts pitch of osc 2 in eighths of a semitone.)\n" +
			"(Mix controls oscillator mix from 0 to 256. Default 128.)";
	}
}
