
package micromod;

public class Macro {
	private Scale scale;
	private int rootKey, speed;
	private Pattern notes;
	
	public Macro( String scale, String rootKey, Pattern notes, int speed ) {
		this.scale = new Scale( scale != null ? scale : Scale.CHROMATIC );
		this.rootKey = Note.parseKey( rootKey != null ? rootKey : "C-2" );
		this.notes = new Pattern( 1, notes );
		this.speed = ( speed > 0 ) ? speed : 6;
	}

	/* Expand macro into the specified pattern until end or an instrument is set. */
	public int expand( Module module, int patternIdx, int channelIdx, int rowIdx ) {
		return expand( module, new int[] { patternIdx }, channelIdx, rowIdx );
	}

	/* Treat the specified patterns as a single large pattern and expand macro
	   until the end or an instrument is set. The final row index is returned. */
	public int expand( Module module, int[] patterns, int channelIdx, int rowIdx ) {
		int macroRowIdx = 0, srcKey = rootKey, dstKey = rootKey, distance = 0, amplitude = 64;
		int volume = 0, fineTune = 0, period = 0, portaPeriod = 0, portaSpeed = 0;
		int sampleOffset = 0, sampleLength = 0, delta;
		Note note = new Note();
		while( macroRowIdx < Pattern.NUM_ROWS ) {
			int patternsIdx = rowIdx / Pattern.NUM_ROWS;
			if( patternsIdx >= patterns.length ) {
				break;
			}
			Pattern pattern = module.getPattern( patterns[ patternsIdx ] );
			pattern.getNote( rowIdx % Pattern.NUM_ROWS, channelIdx, note );
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
				volume = instrument.getVolume() * 16;
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
				portaPeriod = clampPeriod( Note.keyToPeriod( note.key, fineTune ) );
				if( note.effect != 0x3 && note.effect != 0x5 ) {
					period = portaPeriod;
				}
				if( note.effect != 0x9 ) {
					sampleOffset = 0;
				}
			}
			if( note.effect == 0 && note.parameter != 0 ) {
				/* Arpeggio.*/
				delta = scale.getDistance( srcKey, srcKey + ( ( note.parameter >> 4 ) & 0xF ) );
				delta = scale.transpose( dstKey, delta ) - dstKey;
				if( delta < 0 ) delta = 0;
				if( delta > 15 ) delta = ( delta - 3 ) % 12 + 3;
				note.parameter = ( delta << 4 ) + ( note.parameter & 0xF );
				delta = scale.getDistance( srcKey, srcKey + ( note.parameter & 0xF ) );
				delta = scale.transpose( dstKey, delta ) - dstKey;
				if( delta < 0 ) delta = 0;
				if( delta > 15 ) delta = ( delta - 3 ) % 12 + 3;
				note.parameter = ( note.parameter & 0xF0 ) + delta;
			} else if( note.effect == 0x1 || ( note.effect == 0xE && ( note.parameter & 0xF0 ) == 0x10 ) ) {
				if( note.effect == 0x1 ) {
					/* Portamento up. */
					if( note.parameter > 0xF0 && note.parameter < 0xFE ) {
						delta = period - note.transpose( period, note.parameter & 0xF );
					} else {
						delta = note.transpose( note.parameter * ( speed - 1 ), dstKey - srcKey );
					}
					if( delta >= ( speed - 1 ) && speed > 1 ) {
						delta = delta * 2 / ( speed - 1 );
						note.parameter = ( delta >> 1 ) + ( delta & 1 );
						period = clampPeriod( period - note.parameter * ( speed - 1 ) );
					} else {
						note.effect = 0xE;
						note.parameter = 0x10 + ( delta & 0xF );
						period = clampPeriod( period - ( delta & 0xF ) );
					}
				} else {
					/* Fine portamento up. */
					note.parameter = 0x10 + transpose( note.parameter & 0xF, dstKey - srcKey, 0xF );
					period = clampPeriod( period - ( note.parameter & 0xF ) );
				}
			} else if( note.effect == 0x2 || ( note.effect == 0xE && ( note.parameter & 0xF0 ) == 0x20 ) ) {
				if( note.effect == 0x2 ) {
					/* Portamento down. */
					if( note.parameter > 0xF0 && note.parameter < 0xFE ) {
						delta = note.transpose( period, -( note.parameter & 0xF ) ) - period;
					} else {
						delta = note.transpose( note.parameter * ( speed - 1 ), dstKey - srcKey );
					}
					if( delta >= ( speed - 1 ) && speed > 1 ) {
						delta = delta * 2 / ( speed - 1 );
						note.parameter = ( delta >> 1 ) + ( delta & 1 );
						period = clampPeriod( period + note.parameter * ( speed - 1 ) );
					} else {
						note.effect = 0xE;
						note.parameter = 0x20 + ( delta & 0xF );
						period = clampPeriod( period + ( delta & 0xF ) );
					}
				} else {
					/* Fine portamento down. */
					note.parameter = 0x20 + transpose( note.parameter & 0xF, dstKey - srcKey, 0xF );
					period = clampPeriod( period + ( note.parameter & 0xF ) );
				}
			} else if( note.effect == 0x3 || note.effect == 0x5 ) {
				/* Tone portamento. */
				if( note.effect == 0x3 ) {
					if( note.parameter > 0xF0 && note.parameter < 0xFE ) {
						if( portaPeriod < period ) {
							delta = period - note.transpose( period, note.parameter & 0xF );
						} else {
							delta = note.transpose( period, -( note.parameter & 0xF ) ) - period;
						}
						delta = ( speed > 1 ) ? delta * 2 / ( speed - 1 ) : 0;
						delta = ( delta >> 1 ) + ( delta & 1 );
						note.parameter = ( delta > 0 ) ? delta : 1;
					} else {
						note.parameter = transpose( note.parameter & 0xFF, dstKey - srcKey, 0xFF );
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
			} else if( note.effect == 0x9 ) {
				/* Set sample offset. */
				if( note.parameter >= 0xF0 ) {
					sampleOffset = sampleOffset + sampleLength * ( note.parameter & 0xF ) / 16;
					note.parameter = ( ( sampleOffset + ( sampleOffset & 0x80 ) ) >> 8 ) & 0xFF;
				} else {
					sampleOffset = ( note.parameter & 0xFF ) << 8;
				}
			}
			if( note.effect == 0x4 || note.effect == 0x6 ) {
				/* Vibrato. */
				note.parameter = ( note.parameter & 0xF0 ) + transpose( note.parameter & 0xF, dstKey - srcKey, 0xF );
			} else if( note.effect == 0x7 ) {
				/* Tremolo. */
				note.parameter = ( note.parameter & 0xF0 ) + divide( ( note.parameter & 0xF ) * amplitude, 64, 0xF );
			}
			if( note.effect == 0x5 || note.effect == 0x6 || note.effect == 0xA ) {
				/* Volume slide. */
				if( ( note.parameter & 0xF ) == 0xF || ( note.parameter & 0xF0 ) == 0xF0 ) {
					note.effect = 0xC;
					delta = ( ( note.parameter & 0xF ) == 0xF ) ? ( ( note.parameter & 0xF0 ) >> 4 ) : ( note.parameter & 0xF );
					delta = 128 * ( volume > 512 ? 4 : ( volume > 128 ? 2 : 1 ) ) / ( 16 - delta );
					volume = clampVolume( volume + ( ( ( note.parameter & 0xF ) == 0xF ) ? delta : -delta ) );
					note.parameter = ( volume + ( volume & 0x8 ) ) >> 4;
				} else {
					delta = divide( ( ( note.parameter & 0xF0 ) >> 4 ) * amplitude, 64, 0xF );
					note.parameter = ( delta << 4 ) + divide( ( note.parameter & 0xF ) * amplitude, 64, 0xF );
					volume = clampVolume( volume + ( ( ( note.parameter & 0xF0 ) >> 4 ) - ( note.parameter & 0xF ) ) * ( speed - 1 ) * 16 );
				}
			} else if( note.effect == 0xC ) {
				/* Set volume. */
				note.parameter = divide( note.parameter * amplitude, 64, 64 );
				volume = note.parameter * 16;
			} else if( note.effect == 0xE && ( note.parameter & 0xF0 ) == 0xA0 ) {
				/* Fine volume slide up. */
				note.parameter = 0xA0 + divide( ( note.parameter & 0xF ) * amplitude, 64, 15 );
				volume = clampVolume( volume + ( note.parameter & 0xF ) * 16 );
			} else if( note.effect == 0xE && ( note.parameter & 0xF0 ) == 0xB0 ) {
				/* Fine volume slide down. */
				note.parameter = 0xB0 + divide( ( note.parameter & 0xF ) * amplitude, 64, 15 );
				volume = clampVolume( volume - ( note.parameter & 0xF ) * 16 );
			}
			pattern.setNote( ( rowIdx++ ) % pattern.NUM_ROWS, channelIdx, note );
		}
		return rowIdx;
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

	private static int transpose( int period, int semitones, int maximum ) {
		period = Note.transpose( period, semitones );
		return period < maximum ? period : maximum;
	}

	private static int divide( int dividend, int divisor, int maximum ) {
		/* Divide positive integers with rounding and clipping. */
		int quotient = ( dividend << 1 ) / divisor;
		quotient = ( quotient >> 1 ) + ( quotient & 1 );
		return quotient < maximum ? quotient : maximum;
	}
}
