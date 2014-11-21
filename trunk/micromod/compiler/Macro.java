
package micromod.compiler;

public class Macro implements Element {
	private Module parent;
	private Pattern sibling;
	private Scale child = new Scale( this );
	private micromod.Pattern pattern;
	private int macroIdx, rowIdx;
	private int repeatRow, speed;
	private String scale, root;

	public Macro( Module parent ) {
		this.parent = parent;
		sibling = new Pattern( parent );
	}
	
	public String getToken() {
		return "Macro";
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
		pattern = new micromod.Pattern( 1 );
		macroIdx = Parser.parseInteger( value );
		rowIdx = repeatRow = 0;
		speed = 6;
	}

	public void end() {
		calculateSlide( parent.getModule(), pattern, speed );
		parent.setMacro( macroIdx, new micromod.Macro( scale, root, pattern ) );
	}

	public void setScale( String scale ) {
		this.scale = scale;
	}
	
	public void setRoot( String root ) {
		this.root = root;
	}

	public void setSpeed( int ticksPerRow ) {
		speed = ticksPerRow;
	}

	public void nextNote( micromod.Note note, int repeatParam ) {
		pattern.setNote( rowIdx++, 0, note );
		micromod.Module module = parent.getModule();
		if( repeatParam > 0 ) {
			int repeatEnd = rowIdx;
			for( int idx = 1; idx < repeatParam; idx++ ) {
				for( int row = repeatRow; row < repeatEnd; row++ ) {
					pattern.getNote( row, 0, note );
					pattern.setNote( rowIdx++, 0, note );
				}
			}
			repeatRow = rowIdx;
		}
	}

	private static void calculateSlide( micromod.Module module, micromod.Pattern pattern, int speed ) {
		int volume = 0, fineTune = 0, period = 0, portaPeriod = 0, portaSpeed = 0;
		int sampleOffset = 0, sampleLength = 0, delta;
		micromod.Note note = new micromod.Note();
		for( int row = 0; row < 64; row++ ) {
			pattern.getNote( row, 0, note );
			if( note.instrument > 0 ) {
				micromod.Instrument instrument = module.getInstrument( note.instrument );
				volume = instrument.getVolume() * 16;
				fineTune = instrument.getFineTune();
				sampleLength = instrument.getLoopStart() + instrument.getLoopLength();
			}
			if( note.effect == 0xE && ( note.parameter & 0xF0 ) == 0x50 ) {
				fineTune = ( note.parameter & 0x7 ) - ( note.parameter & 0x8 );
			}
			if( note.key > 0 ) {
				portaPeriod = micromod.Note.keyToPeriod( note.key, fineTune );
				if( note.effect != 0x3 && note.effect != 0x5 ) {
					period = portaPeriod;
				}
				if( note.effect != 0x9 ) {
					sampleOffset = 0;
				}
			}
			if( note.effect == 0x1 ) {
				if( note.parameter > 0xF0 ) {
					delta = note.transpose( period, ( note.parameter & 0xF ) );
					delta = ( speed > 1 ) ? ( period - delta ) * 2 / ( speed - 1 ) : 0;
					note.parameter = ( delta >> 1 ) + ( delta & 1 );
				}
				period = clampPeriod( period - note.parameter * ( speed - 1 ) );
			} else if( note.effect == 0x2 ) {
				if( note.parameter > 0xF0 && ( ( note.parameter & 0xF ) < 0xE ) ) {
					delta = note.transpose( period, -( note.parameter & 0xF ) );
					delta = ( speed > 1 ) ? ( delta - period ) * 2 / ( speed - 1 ) : 0;
					note.parameter = ( delta >> 1 ) + ( delta & 1 );
				}
				period = clampPeriod( period + note.parameter * ( speed - 1 ) );
			} else if( note.effect == 0x3 || note.effect == 0x5 ) {
				if( note.effect == 0x3 ) {
					if( note.parameter > 0xF0 && ( ( note.parameter & 0xF ) < 0xE ) ) {
						if( portaPeriod < period ) {
							delta = note.transpose( period, ( note.parameter & 0xF ) );
							delta = ( speed > 1 ) ? ( period - delta ) * 2 / ( speed - 1 ) : 0;
						} else {
							delta = note.transpose( period, -( note.parameter & 0xF ) );
							delta = ( speed > 1 ) ? ( delta - period ) * 2 / ( speed - 1 ) : 0;
						}
						note.parameter = ( delta >> 1 ) + ( delta & 1 );
					}
					portaSpeed = note.parameter;
				}
				if( period < portaPeriod ) {
					period = clampPeriod( period + portaSpeed * ( speed - 1 ) );
					if( period > portaPeriod ) {
						period = portaPeriod;
					}
				} else {
					period = clampPeriod( period - portaSpeed * ( speed - 1 ) );
					if( period < portaPeriod ) {
						period = portaPeriod;
					}
				}
			} else if( note.effect == 0x9 ) {
				if( note.parameter > 0xF0 ) {
					sampleOffset = sampleOffset + sampleLength * ( note.parameter & 0xF ) / 16;
					note.parameter = ( ( sampleOffset + ( sampleOffset & 0x80 ) ) >> 8 ) & 0xFF;
				}
			} else if( note.effect == 0x5 || note.effect == 0x6 || note.effect == 0xA ) {
				if( ( note.parameter & 0xF ) == 0xF || ( note.parameter & 0xF0 ) == 0xF0 ) {
					note.effect = 0xC;
					delta = ( ( note.parameter & 0xF ) == 0xF ) ? ( ( note.parameter & 0xF0 ) >> 4 ) : ( note.parameter & 0xF );
					delta = 256 * ( ( volume > 384 ) ? 2 : 1 ) / ( 16 - delta );
					volume = clampVolume( volume + ( ( ( note.parameter & 0xF ) == 0xF ) ? delta : -delta ) );
					note.parameter = volume / 16;
				} else {
					volume = clampVolume( volume + ( ( ( note.parameter & 0xF0 ) >> 4 ) - ( note.parameter & 0xF ) ) * ( speed - 1 ) * 16 );
				}
			} else if( note.effect == 0xC ) {
				volume = clampVolume( note.parameter * 16 );
			} else if( note.effect == 0xE ) {
				if( ( note.parameter & 0xF0 ) == 0x10 ) {
					period = clampPeriod( period - ( note.parameter & 0xF ) );
				} else if( ( note.parameter & 0xF0 ) == 0x20 ) {
					period = clampPeriod( period + ( note.parameter & 0xF ) );
				} else if( ( note.parameter & 0xF0 ) == 0xA0 ) {
					volume = clampVolume( volume + ( note.parameter & 0xF ) * 16 );
				} else if( ( note.parameter & 0xF0 ) == 0xB0 ) {
					volume = clampVolume( volume - ( note.parameter & 0xF ) * 16 );
				}
			}
			pattern.setNote( row, 0, note );
		}
	}

	private static int clampVolume( int volume ) {
		if( volume < 0 ) {
			volume = 0;
		} else if( volume > 1024 ) {
			volume = 1024;
		}
		return volume;
	}

	private static int clampPeriod( int period ) {
		if( period < 7 ) {
			period = 6848;
		}
		return period;
	}

	private static int sqrt( int y ) {
		int x = 16;
		for( int n = 0; n < 6; n++ ) {
			x = ( x + y / x );
			x = ( x >> 1 ) + ( x & 1 );
		}
		return x;
	}
}
