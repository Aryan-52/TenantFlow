import type { CSSProperties, ReactNode } from 'react';

/**
 * Decorative isometric workspace for the sign-in pages, built from CSS 3D boxes: a slab, a
 * raised project board with stacked task cards (one lifted off the board), a timeline tray,
 * extruded progress columns and two cards floating towards the viewer. Plain HTML + CSS
 * transforms: no canvas, no 3D library, nothing the form waits for. Hidden from assistive
 * technology, never focusable, and still when reduced motion is preferred.
 */

interface BlockProps {
  /** Position and footprint on the parent surface, in px. */
  x: number;
  y: number;
  w: number;
  d: number;
  /** Extrusion height, in px. */
  h: number;
  /** Extra lift above the surface, in px. */
  z?: number;
  tone?: 'slab' | 'tray' | 'card' | 'accent' | 'done' | 'column';
  className?: string;
  /** Rendered on the block's top face (which is itself a 3D surface). */
  children?: ReactNode;
}

/** An extruded box: a top face plus the two side faces the camera can see. */
const Block = ({ x, y, w, d, h, z = 0, tone = 'card', className = '', children }: BlockProps) => (
  <div
    className={`iso-block tone-${tone} ${className}`.trim()}
    style={{ left: x, top: y, width: w, height: d, '--h': `${h}px`, '--z': `${z}px` } as CSSProperties}
  >
    <div className="iso-face-front" />
    <div className="iso-face-side" />
    <div className="iso-face-top">{children}</div>
  </div>
);

/** A soft shadow cast on a surface by something hovering above it. */
const Shadow = ({ x, y, w, d }: { x: number; y: number; w: number; d: number }) => (
  <span className="iso-shadow" style={{ left: x, top: y, width: w, height: d }} />
);

const LANES = [
  { x: 10, cards: ['', 'accent', ''] },
  { x: 86, cards: ['', ''] },
  { x: 162, cards: ['done', 'done'] },
] as const;

export const AuthScene = () => (
  <div className="auth-scene" aria-hidden="true">
    <div className="iso-stage">
      <div className="iso-plane">
        <Block x={0} y={0} w={460} d={460} h={18} tone="slab">
          {/* Project board: three lanes of task cards */}
          <Block x={26} y={26} w={244} d={214} h={10} tone="tray" className="iso-board">
            <span className="iso-label" style={{ left: 12, top: 10 }} />
            {LANES.map((lane) => (
              <div key={lane.x} className="iso-lane" style={{ left: lane.x, top: 30, width: 70, height: 172 }}>
                {lane.cards.map((tone, i) => (
                  <Block key={i} x={6} y={8 + i * 40} w={58} d={32} h={5} tone={tone || 'card'} />
                ))}
              </div>
            ))}
            {/* The card being moved: lifted off the board, casting a shadow onto it */}
            <Shadow x={92} y={122} w={62} d={36} />
            <Block x={90} y={112} w={62} d={34} h={6} z={46} tone="accent" className="iso-lifted" />
          </Block>

          {/* Timeline tray with schedule bars and a milestone */}
          <Block x={26} y={266} w={244} d={168} h={8} tone="tray">
            <span className="iso-label" style={{ left: 12, top: 10 }} />
            <Block x={14} y={34} w={120} d={16} h={4} tone="column" />
            <Block x={70} y={62} w={140} d={16} h={4} tone="accent" />
            <Block x={30} y={90} w={86} d={16} h={4} tone="done" />
            <Block x={120} y={118} w={92} d={16} h={4} tone="column" />
            <span className="iso-milestone" style={{ left: 214, top: 64 }} />
          </Block>

          {/* Progress columns */}
          {[
            { y: 300, h: 34 },
            { y: 340, h: 58 },
            { y: 380, h: 86 },
          ].map((c, i) => (
            <Block key={i} x={300} y={c.y} w={30} d={30} h={c.h} tone="column" />
          ))}
          <Block x={350} y={380} w={30} d={30} h={128} tone="accent" />
          <Block x={300} y={250} w={130} d={26} h={3} tone="tray" />

          {/* Floating cards that face the viewer */}
          <div className="iso-billboard iso-float-a" style={{ left: 330, top: 150, '--z': '64px' } as CSSProperties}>
            <div className="iso-card">
              <div className="iso-card-row">
                <span className="iso-ring"><span>68%</span></span>
                <div className="iso-card-text">
                  <strong>Launch plan</strong>
                  <span>14 of 21 tasks done</span>
                </div>
              </div>
              <div className="iso-progress"><span style={{ width: '68%' }} /></div>
              <div className="iso-avatars"><i /><i /><i /></div>
            </div>
          </div>
          <div className="iso-billboard iso-float-b" style={{ left: 40, top: 330, '--z': '70px' } as CSSProperties}>
            <div className="iso-chip"><span className="iso-check" />Review approved</div>
          </div>
        </Block>
      </div>
    </div>
  </div>
);
