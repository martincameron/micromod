
package micromod;

public class Macro {
	private Scale scale;
	private int rootKey;
	private Pattern notes;
	
	public Macro( String scale, String rootKey, Pattern notes ) {
		this.scale = new Scale( scale != null ? scale : Scale.CHROMATIC );
		this.rootKey = Note.parseKey( rootKey != null ? rootKey : "C-2" );
		this.notes = new Pattern( 1, notes );
	}

	/* Expand macro into the specified position in the pattern list.
	   The speed parameter must match the effect 0xF speed for proper transpose (default 6).
	   Expansion will continue into the next pattern until an instrument is encountered. */
	public int expand( Module module, Pattern[] patterns, int patternIdx, int rowIdx, int channelIdx, int speed ) {
		int macroRowIdx = 0, srcKey = rootKey, dstKey = rootKey, distance = 0, amplitude = 64;
		int volume = 0, fineTune = 0, period = 0, portaPeriod = 0, portaSpeed = 0;
		int sampleOffset = 0, sampleLength = 0, delta;
		if( speed < 1 ) {
			speed = 1;
		}
		Note note = new Note();
		while( macroRowIdx < Pattern.NUM_ROWS ) {
			patternIdx += rowIdx / Pattern.NUM_ROWS;
			rowIdx = rowIdx % Pattern.NUM_ROWS;
			if( patternIdx >= patterns.length ) {
				break;
			}
			Pattern pattern = patterns[ patternIdx ];
			pattern.getNote( rowIdx, channelIdx, note );
			if( note.instrument > 0 ) {
				break;
			}
			if( note.key > 0 ) {
				distance = scale.getDistance( rootKey, note.key );
			}
			int effect = note.effect;
			int param = note.parameter;
			notes.getNote( macroRowIdx++, 0, note );
			if( effect > 0 ) {
				if( effect == 0xC ) {
					amplitude = param;
				}
				if( effect != 0xC || param == 0 ) {
					/* Ensure C00 is passed through to empty notes. */
					note.effect = effect;
					note.parameter = param;
				}
			}
			if( note.instrument > 0 ) {
				Instrument instrument = module.getInstrument( note.instrument );
				volume = instrument.getVolume() * 4;
				fineTune = instrument.getFineTune();
				sampleLength = instrument.getLoopStart() + instrument.getLoopLength();
				if( amplitude < 64 && note.effect != 0xC ) {
					/* Setting an instrument sets the volume. */
					note.effect = 0xC;
					note.parameter = instrument.getVolume();
				}
			}
			if( note.effect == 0xE && ( note.parameter & 0xF0 ) == 0x50 ) {
				fineTune = ( note.parameter & 0x7 ) - ( note.parameter & 0x8 );
			}
			if( note.key > 0 ) {
				srcKey = note.key;
				dstKey = scale.transpose( srcKey, distance );
				note.key = dstKey;
				portaPeriod = Note.keyToPeriod( note.key, fineTune );
				if( note.effect != 0x3 && note.effect != 0x5 ) {
					period = portaPeriod;
				}
				if( note.effect != 0x9 ) {
					sampleOffset = 0;
				}
			}
			if( volume < 0 ) {
				volume = 0;
			}
			if( volume > 256 ) {
				volume = 256;
			}
			if( period < 0 ) {
				period = 0;
			}
			if( note.effect == 0 && note.parameter != 0 ) {
				/* Arpeggio.*/
				delta = scale.getDistance( srcKey, srcKey + ( ( note.parameter >> 4 ) & 0xF ) );
				delta = scale.transpose( dstKey, delta ) - dstKey;
				if( delta < 0 ) {
					delta = 0;
				}
				if( delta > 15 ) {
					delta = ( delta - 3 ) % 12 + 3;
				}
				note.parameter = ( delta << 4 ) + ( note.parameter & 0xF );
				delta = scale.getDistance( srcKey, srcKey + ( note.parameter & 0xF ) );
				delta = scale.transpose( dstKey, delta ) - dstKey;
				if( delta < 0 ) {
					delta = 0;
				}
				if( delta > 15 ) {
					delta = ( delta - 3 ) % 12 + 3;
				}
				note.parameter = ( note.parameter & 0xF0 ) + delta;
			} else if( note.effect == 0x1 ) {
				/* Portamento up. */
				if( note.parameter > 0xF0 && note.parameter < 0xFE ) {
					delta = period - Note.keyToPeriod( Note.periodToKey( period ) + ( note.parameter & 0xF ), 0 );
				} else {
					delta = Note.transpose( note.parameter * ( speed - 1 ), dstKey - srcKey );
				}
				note.parameter = ( speed > 1 ) ? divide( delta, speed - 1, 0xFF ) : 0;
				period = period - note.parameter * ( speed - 1 );
				if( note.parameter == 0 ) {
					note.effect = 0xE;
					note.parameter = 0x10 + ( delta & 0xF );
					period = period - ( delta & 0xF );
				}
			} else if( note.effect == 0x2 ) {
				/* Portamento down. */
				if( note.parameter > 0xF0 && note.parameter < 0xFE ) {
					delta = Note.keyToPeriod( Note.periodToKey( period ) - ( note.parameter & 0xF ), 0 ) - period;
				} else {
					delta = Note.transpose( note.parameter * ( speed - 1 ), dstKey - srcKey );
				}
				note.parameter = ( speed > 1 ) ? divide( delta, speed - 1, 0xFF ) : 0;
				period = period + note.parameter * ( speed - 1 );
				if( note.parameter == 0 ) {
					note.effect = 0xE;
					note.parameter = 0x20 + ( delta & 0xF );
					period = period + ( delta & 0xF );
				}
			} else if( note.effect == 0x3 || note.effect == 0x5 ) {
				/* Tone portamento. */
				if( note.effect == 0x3 && note.parameter > 0 ) {
					if( note.parameter > 0xF0 && note.parameter < 0xFE ) {
						if( portaPeriod < period ) {
							delta = period - Note.transpose( period, note.parameter & 0xF );
						} else {
							delta = Note.transpose( period, -( note.parameter & 0xF ) ) - period;
						}
						note.parameter = divide( delta, ( speed > 1 ) ? ( speed - 1 ) : 1, 0xFF );
					} else {
						note.parameter = transpose( note.parameter & 0xFF, dstKey - srcKey, 0xFF );
					}
					if( note.parameter < 1 ) {
						note.parameter = 1;
					}
					portaSpeed = note.parameter;
				}
				if( period < portaPeriod ) {
					period = period + portaSpeed * ( speed - 1 );
					if( period > portaPeriod ) {
						period = portaPeriod;
					}
				} else {
					period = period - portaSpeed * ( speed - 1 );
					if( period < portaPeriod ) {
						period = portaPeriod;
					}
				}
			} else if( note.effect == 0x4 ) {
				/* Vibrato. */
				note.parameter = ( note.parameter & 0xF0 ) + transpose( note.parameter & 0xF, dstKey - srcKey, 0xF );
			} else if( note.effect == 0x9 ) {
				/* Set sample offset. */
				if( note.parameter >= 0xF0 ) {
					sampleOffset = sampleOffset + sampleLength * ( note.parameter & 0xF ) / 64;
					note.parameter = divide( sampleOffset, 256, 0xFF );
				} else {
					sampleOffset = ( note.parameter & 0xFF ) << 8;
				}
			} else if( note.effect == 0xE && ( note.parameter & 0xF0 ) == 0x10 ) {
				/* Fine portamento up. */
				note.parameter = 0x10 + transpose( note.parameter & 0xF, dstKey - srcKey, 0xF );
				period = period - ( note.parameter & 0xF );
			} else if( note.effect == 0xE && ( note.parameter & 0xF0 ) == 0x20 ) {
				/* Fine portamento down. */
				note.parameter = 0x20 + transpose( note.parameter & 0xF, dstKey - srcKey, 0xF );
				period = period + ( note.parameter & 0xF );
			}
			if( note.effect == 0x5 || note.effect == 0x6 || note.effect == 0xA ) {
				/* Volume slide. */
				if( ( note.parameter & 0xF ) == 0xF ) {
					delta = ( ( note.parameter & 0xF0 ) >> 4 ) + 1;
					delta = sqrt( volume * 256 ) + divide( 256, delta * 4, 256 );
					delta = divide( delta * delta, 256, 256 ) - volume;
					if( delta < 1 ) {
						delta = 1;
					}
				} else if( ( note.parameter & 0xF0 ) == 0xF0 ) {
					delta = ( note.parameter & 0xF ) + 1;
					delta = sqrt( volume * 256 ) - divide( 256, delta * 4, 256 );
					if( delta < 0 ) {
						delta = 0;
					}
					delta = divide( delta * delta, 256, 256 ) - volume;
					if( delta > -1 ) {
						delta = -1;
					}
				} else {
					delta = divide( ( ( note.parameter & 0xF0 ) >> 4 ) * 4 * amplitude, 64, 256 );
					delta = delta - divide( ( note.parameter & 0xF ) * 4 * amplitude, 64, 256 );
					delta = delta * ( speed - 1 );
				}
				if( volume + delta > amplitude * 4 ) {
					delta = amplitude * 4 - volume;
				}
				if( speed > 1 && divide( delta, ( speed - 1 ) * 4, 0xF ) > 1 ) {
					delta = divide( delta, ( speed - 1 ) * 4, 0xF );
					note.parameter = delta << 4;
					volume = volume + delta * ( speed - 1 ) * 4;
				} else if( speed > 1 && divide( -delta, ( speed - 1 ) * 4, 0xF ) > 1 ) {
					delta = divide( -delta, ( speed - 1 ) * 4, 0xF );
					note.parameter = delta;
					volume = volume - delta * ( speed - 1 ) * 4;
				} else if( note.effect == 0xA ) {
					volume = volume + delta;
					note.effect = 0xC;
					note.parameter = divide( volume, 4, 64 );
				} else if( delta > 0 ) {
					note.parameter = 0x10;
					volume = volume + ( speed - 1 ) * 4;
				} else if( delta < 0 ) {
					note.parameter = 0x1;
					volume = volume - ( speed - 1 ) * 4;
				}
			} else if( note.effect == 0x7 ) {
				/* Tremolo. */
				note.parameter = ( note.parameter & 0xF0 ) + divide( ( note.parameter & 0xF ) * amplitude, 64, 0xF );
			} else if( note.effect == 0xC ) {
				/* Set volume. */
				if( ( note.parameter & 0xF0 ) == 0xF0 ) {
					note.parameter = ( note.parameter & 0xF ) + 1;
					note.parameter = ( note.parameter * note.parameter ) >> 2;
				}
				note.parameter = divide( note.parameter * amplitude, 64, amplitude );
				volume = note.parameter * 4;
			} else if( note.effect == 0xE && ( note.parameter & 0xF0 ) == 0xA0 ) {
				/* Fine volume slide up. */
				delta = divide( ( note.parameter & 0xF ) * 4 * amplitude, 64, 256 );
				volume = volume + delta;
				note.effect = 0xC;
				note.parameter = divide( volume, 4, amplitude );
			} else if( note.effect == 0xE && ( note.parameter & 0xF0 ) == 0xB0 ) {
				/* Fine volume slide down. */
				delta = divide( ( note.parameter & 0xF ) * 4 * amplitude, 64, 256 );
				volume = volume - delta;
				note.effect = 0xC;
				note.parameter = divide( volume, 4, amplitude );
			}
			pattern.setNote( rowIdx++, channelIdx, note );
		}
		return rowIdx;
	}

	private static int transpose( int period, int semitones, int maximum ) {
		period = Note.transpose( period, semitones );
		return period < maximum ? period : maximum;
	}

	private static int divide( int dividend, int divisor, int maximum ) {
		/* Divide positive integers with rounding and clipping. */
		int quotient = 0;
		if( dividend > 0 ) {
			quotient = ( dividend << 1 ) / divisor;
			quotient = ( quotient >> 1 ) + ( quotient & 1 );
			if( quotient > maximum ) {
				quotient = maximum;
			}
		}
		return quotient;
	}

	private static int sqrt( int y ) {
		int x = 256;
		for( int n = 0; n < 8; n++ ) {
			x = ( x + y / x );
			x = ( x >> 1 ) + ( x & 1 );
		}
		return x;
	}
}
