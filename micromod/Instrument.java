
package micromod;

public class Instrument {
	public static final int FP_SHIFT = 15, FP_ONE = 1 << FP_SHIFT, FP_MASK = FP_ONE - 1;
	
	private String name = "";
	private int volume, fineTune, loopStart, loopLength;
	private byte[] sampleData = new byte[ 0 ];
	
	public String getName() {
		return name;
	}
	
	public void setName( String name ) {
		if( name == null ) {
			this.name = "";
		} else if( name.length() > 22 ) {
			this.name = name.substring( 0, 22 );
		} else {
			this.name = name;
		}
	}

	public int getVolume() {
		return volume;
	}
	
	public void setVolume( int volume ) {
		if( volume < 0 || volume > 64 ) {
			throw new IllegalArgumentException( "Instrument volume out of range (0 to 64): " + volume );
		}
		this.volume = volume;
	}
	
	public int getFineTune() {
		return fineTune;
	}
	
	public void setFineTune( int fineTune ) {
		if( fineTune < -8 || fineTune > 7 ) {
			throw new IllegalArgumentException( "Instrument fine tune out of range (-8 to 7): " + fineTune );
		}
		this.fineTune = fineTune;
	}

	public byte[] getSampleData() {
		byte[] data = new byte[ loopStart + loopLength ];
		System.arraycopy( sampleData, 0, data, 0, data.length );
		return data;
	}

	public void setSampleData( byte[] sampleData, int loopStart, int loopLength, boolean pingPong ) {
		setSampleData( sampleData, 0, sampleData.length, loopStart, loopLength, pingPong );
	}
	
	public void setSampleData( byte[] sampleData, int sampleOffset, int sampleLength, int loopStart, int loopLength, boolean pingPong ) {
		/* LoopStart and LoopLength must always be even. */
		loopStart = loopStart & -2;
		loopLength = loopLength & -2;
		/* Correct loop points. */
		if( loopStart + loopLength > sampleLength ) {
			loopLength = ( sampleLength - loopStart ) & -2;
		}
		if( loopStart < 0 || loopStart >= sampleLength || loopLength < 4 ) {
			loopStart = sampleLength & -2;
			loopLength = 0;
		}
		sampleLength = loopStart + loopLength;
		if( pingPong ) {
			loopLength = loopLength * 2;
		}
		/* Maximum sample size is 128k. */
		if( loopStart + loopLength > 0x1FFFE ) {
			throw new IllegalArgumentException( "Sample data length out of range (0-131070): " + sampleLength );
		}
		this.loopStart = loopStart;
		this.loopLength = loopLength;
		this.sampleData = new byte[ loopStart + loopLength + 1 ];
		System.arraycopy( sampleData, sampleOffset, this.sampleData, 0, sampleLength );
		if( pingPong ) {
			for( int idx = 0, end = loopLength / 2; idx < end; idx++ ) {
				this.sampleData[ sampleLength + idx ] = this.sampleData[ sampleLength - idx - 1 ];
			}
		}
		/* The sample after the loop end must be the same as the loop start for the interpolation algorithm. */
		this.sampleData[ loopStart + loopLength ] = this.sampleData[ loopStart ];
	}
	
	public int getLoopStart() {
		return loopStart;
	}
	
	public int getLoopLength() {
		return loopLength;
	}
	
	public void getAudio( int sampleIdx, int sampleFrac, int step, int leftGain, int rightGain,
		int[] mixBuffer, int offset, int count, boolean interpolation ) {
		int loopEnd = loopStart + loopLength;
		int mixIdx = offset << 1;
		int mixEnd = ( offset + count ) << 1;
		if( interpolation ) {
			while( mixIdx < mixEnd ) {
				if( sampleIdx >= loopEnd ) {
					if( loopLength < 2 ) {
						break;
					}
					while( sampleIdx >= loopEnd ) {
						sampleIdx -= loopLength;
					}
				}
				int c = sampleData[ sampleIdx ];
				int m = sampleData[ sampleIdx + 1 ] - c;
				int y = ( ( m * sampleFrac ) >> ( FP_SHIFT - 8 ) ) + ( c << 8 );
				mixBuffer[ mixIdx++ ] += ( y * leftGain ) >> FP_SHIFT;
				mixBuffer[ mixIdx++ ] += ( y * rightGain ) >> FP_SHIFT;
				sampleFrac += step;
				sampleIdx += sampleFrac >> FP_SHIFT;
				sampleFrac &= FP_MASK;
			}
		} else {
			while( mixIdx < mixEnd ) {
				if( sampleIdx >= loopEnd ) {
					if( loopLength < 2 ) {
						break;
					}
					while( sampleIdx >= loopEnd ) {
						sampleIdx -= loopLength;
					}
				}
				int y = sampleData[ sampleIdx ] << 8;
				mixBuffer[ mixIdx++ ] += ( y * leftGain ) >> FP_SHIFT;
				mixBuffer[ mixIdx++ ] += ( y * rightGain ) >> FP_SHIFT;
				sampleFrac += step;
				sampleIdx += sampleFrac >> FP_SHIFT;
				sampleFrac &= FP_MASK;
			}
		}
	}

	public int normalizeSampleIdx( int sampleIdx ) {
		int loopOffset = sampleIdx - loopStart;
		if( loopOffset > 0 ) {
			sampleIdx = loopStart;
			if( loopLength > 1 ) {
				sampleIdx += loopOffset % loopLength;
			}
		}
		return sampleIdx;
	}

	public int load( byte[] module, int instIdx, int sampleDataOffset ) {
		char[] name = new char[ 22 ];
		for( int idx = 0; idx < name.length; idx++ ) {
			int chr = module[ instIdx * 30 - 10 + idx ] & 0xFF;
			name[ idx ] = chr > 32 ? ( char ) chr : 32;
		}
		setName( new String( name ) );
		int sampleLength = calculateSampleDataLength( module, instIdx );
		int fineTune = module[ instIdx * 30 + 14 ] & 0xF;
		setFineTune( fineTune > 7 ? fineTune - 16 : fineTune );
		int volume =  module[ instIdx * 30 + 15 ] & 0x7F;
		setVolume( volume > 64 ? 64 : volume );
		int loopStart = ubeShort( module, instIdx * 30 + 16 ) * 2;
		int loopLength = ubeShort( module, instIdx * 30 + 18 ) * 2;
		setSampleData( module, sampleDataOffset, sampleLength, loopStart, loopLength, false );
		return sampleDataOffset + sampleLength;
	}
	
	public int save( byte[] module, int instIdx, int sampleDataOffset ) {
		int sampleLength = loopStart + loopLength;
		if( module != null ) {
			for( int idx = 0; idx < 22; idx++ ) {
				int chr = idx < name.length() ? name.charAt( idx ) : 32;
				module[ instIdx * 30 - 10 + idx ] = ( byte ) chr;
			}
			module[ instIdx * 30 + 12 ] = ( byte ) ( sampleLength >> 9 );
			module[ instIdx * 30 + 13 ] = ( byte ) ( sampleLength >> 1 );
			module[ instIdx * 30 + 14 ] = ( byte ) ( fineTune < 0 ? fineTune + 16 : fineTune );
			module[ instIdx * 30 + 15 ] = ( byte ) volume;
			module[ instIdx * 30 + 16 ] = ( byte ) ( loopStart >> 9 );
			module[ instIdx * 30 + 17 ] = ( byte ) ( loopStart >> 1 );
			module[ instIdx * 30 + 18 ] = ( byte ) ( loopLength >> 9 );
			module[ instIdx * 30 + 19 ] = ( byte ) ( loopLength >> 1 );
			System.arraycopy( sampleData, 0, module, sampleDataOffset, sampleLength );
		}
		return sampleDataOffset + sampleLength;
	}

	public static int calculateSampleDataLength( byte[] moduleHeader, int instIdx ) {
		return ubeShort( moduleHeader, instIdx * 30 + 12 ) * 2;
	}

	private static int ubeShort( byte[] buffer, int offset ) {
		return ( ( buffer[ offset ] & 0xFF ) << 8 ) | ( buffer[ offset + 1 ] & 0xFF );
	}
}
