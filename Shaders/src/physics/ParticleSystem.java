package physics;
import java.util.ArrayList;


public class ParticleSystem {
	private ArrayList<Particle> particles;
	private CellGrid cube;

	private static final Vector3 GRAVITY = new Vector3(0f, -9.8f, 0f);
	private static final float deltaT = 0.1f;
	private static final float H = 1f;
	private static final float KPOLY = (float) (315f / (64f * Math.PI * Math.pow(H, 9)));
	//We may want to damp the spikey density
	private static final float SPIKY = (float) (45f / (Math.PI * Math.pow(H, 6)));
	private static final float REST_DENSITY = 1f;
	private static final float EPSILON = .1f; // what value?
	private static final float C = 0.01f;

	
	public ArrayList<shade.Vector3> getPositions(){
		ArrayList<shade.Vector3> positions = new ArrayList<shade.Vector3>();
		for (Particle part: particles){
			Vector3 pos = part.getOldPos();
			positions.add(new shade.Vector3(pos.x,pos.y,pos.z));
		}
		return positions;
	}
	
	public ParticleSystem() {
		particles = new ArrayList<Particle>();
		for (int i = 0; i < 10; i++) {
			for (int j = 10; j < 20; j++) {
				for (int k = 0; k < 10; k++) {
					particles.add(new Particle(new Vector3(i, j, k), 1));
				}
			}
		}

		//create cell cube
		cube = new CellGrid(15, 15, 15); //should be whatever the size of our box is
	}

	public void update() {
		applyGravity();
		for (Particle p : particles) {
			//update velocity vi = vi + delta T * fext
			p.setVelocity(p.getVelocity().add(p.getForce().mul(deltaT)));

			//predict position x* = xi + delta T * vi
			p.setNewPos(p.getOldPos().add(p.getVelocity().mul(deltaT)));
		}

		//get neighbors
		cube.updateCells(particles);
		for (Particle p : particles) {
			p.setNeighbors(p.getCell().getParticles());
		}

		//while sovler < iterations (they say that 2-4 is enough in the paper)
		for (int i = 0; i < 4; i++) {

			//Set lambda
			for (Particle p : particles) {
				ArrayList<Particle> neighbors = p.getNeighbors();
				p.setLambda(lambda(p, neighbors));
			}

			for (Particle p : particles) {
				//update position - delta Pi - requires lambda
				Vector3 deltaP = new Vector3 (0f, 0f, 0f);
				ArrayList<Particle> neighbors = p.getNeighbors();
				for (Particle n : neighbors) {
					float lambdaSum = p.getLambda() + n.getLambda();
					deltaP = (deltaP.add(WSpiky(p.getNewPos(), n.getNewPos()))).mul(lambdaSum);
				}
				p.setDeltaP(deltaP.div(REST_DENSITY));
				//collision detection including with box
			}

			for (Particle p : particles) {
				//update x*i = x*i + delta Pi
				p.setNewPos(p.getNewPos().add(p.getDeltaP()));
			}
		}

		for (Particle p : particles) {
			//set new velocity vi = (1/delta T) * (x*i - xi)
			p.setVelocity(p.getNewPos().sub(p.getOldPos()).div(deltaT));

			//apply vorticity confinement
			curl(p);
			applyVorticity(p);
			
			p.setVelocity(p.getVelocity().add(p.getForce().mul(deltaT)));
			p.setNewPos(p.getOldPos().add(p.getVelocity().mul(deltaT)));

			//apply XSPH viscosity

			//update position xi = x*i
			p.setOldPos(p.getNewPos());
		}
	}

	private void applyGravity() {
		for (Particle p : particles) {
			p.getForce().add(GRAVITY);
		}
	}

	//Poly6 Kernel
	private float WPoly6(Vector3 pi, Vector3 pj) {
		float rSquared = (float) pi.sub(pj).magnitude();
		if (rSquared > H) return 0;
		rSquared = (float)Math.pow(rSquared, 2);
		return (float) (KPOLY * Math.pow((H - rSquared), 3));
	}
	
	//Spiky Kernel
	private Vector3 WSpiky (Vector3 pi, Vector3 pj) {
		Vector3 radius = pi.sub(pj);
		float coeff = (float) Math.pow(H - radius.magnitude(), 2);
		coeff *= SPIKY;
		coeff /= radius.magnitude();
		return radius.mul(coeff);
	}

	private float lambda(Particle p, ArrayList<Particle> neighbors) {
		float densityConstraint = calcDensityConstraint (p, neighbors);
		Vector3 gradientI = new Vector3 (0f, 0f, 0f);
		float sumGradients = 0;
		for (Particle n : neighbors) {
			//Calculate gradient with respect to j
			Vector3 gradientJ = WSpiky (p.getNewPos(), n.getNewPos()).div(REST_DENSITY);
			//Add magnitude squared to sum
			sumGradients += Math.pow(gradientJ.magnitude(), 2);
			//Continue calculating particle i gradient
			gradientI = gradientI.add (gradientJ);
		}
		//Add the particle i gradient magnitude squared to sum
		sumGradients += Math.pow(gradientI.magnitude(), 2);
		return  ((-1f) * densityConstraint) / (sumGradients + EPSILON);
	}

	private float calcDensityConstraint (Particle p, ArrayList<Particle> neighbors) {
		float sum = 0f;
		for (Particle n : neighbors) {
			sum += n.getMass() * WPoly6(p.getNewPos(), n.getNewPos());
		}
		return sum / p.getDensity();
	}
	private void curl(Particle p) {
		Vector3 w = new Vector3(0, 0, 0);
		Vector3 velocityDiff;
		Vector3 gradient;
		ArrayList<Particle> neighbors = p.getNeighbors();
		for (Particle n : neighbors) {
			velocityDiff = n.getVelocity().sub(p.getVelocity());
			gradient = WSpiky(p.getNewPos(), n.getNewPos());
			w.add(velocityDiff.cross(gradient));
		}

		p.setCurl(w);
	}

	private void applyVorticity(Particle p) {
		Vector3 N;
		Vector3 w = p.getCurl();
		Vector3 gradient = new Vector3(0, 0, 0);
		Vector3 vorticity;
		ArrayList<Particle> neighbors = p.getNeighbors();
		for (Particle n : neighbors) {
			Vector3 d = n.getNewPos().sub(p.getNewPos());
			Vector3 mw = n.getCurl().sub(w);
			float magnitudeW = mw.magnitude();
			gradient.x += magnitudeW / d.x;
			gradient.y += magnitudeW / d.y;
			gradient.z += magnitudeW / d.z;
		}

		N = gradient.div(gradient.magnitude());
		vorticity = (N.cross(w)).mul(EPSILON);
		p.getForce().add(vorticity);
	}
}
